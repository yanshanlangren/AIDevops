package com.example.aidevops.llm;

import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.model.LlmPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmGatewayChatAbcTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> initRequest = new AtomicReference<JsonNode>();
    private final AtomicReference<JsonNode> chatRequest = new AtomicReference<JsonNode>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chatabc/init_session", exchange -> {
            initRequest.set(mapper.readTree(exchange.getRequestBody()));
            respond(exchange, "application/json",
                    "{\"resCode\":\"FAIAG0000\",\"resMessage\":\"SUCCESS\","
                            + "\"data\":{\"session_id\":\"session-test-1\"}}");
        });
        server.createContext("/chatabc/chat", exchange -> {
            chatRequest.set(mapper.readTree(exchange.getRequestBody()));
            String plan = "{\"root_cause_hypothesis\":\"RowKey规则不一致\","
                    + "\"change_plan\":\"统一生成规则\"}";
            String stream = "event:chunk\n"
                    + "data:" + mapper.writeValueAsString(singleContent(plan)) + "\n\n"
                    + "event:done\n"
                    + "data:{\"status\":\"success\",\"rescode\":\"FAIAG0000\"}\n\n";
            respond(exchange, "text/event-stream", stream);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void initializesSessionThenStreamsChat() {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("chatabc");
        properties.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTimeoutSeconds(5);
        properties.setMaxAttempts(1);
        properties.getChatabc().setAppId("app-1");
        properties.getChatabc().setTrCode("tr-1");
        properties.getChatabc().setTrVersion("v1");
        properties.getChatabc().setAgentId("agent-1");
        properties.getChatabc().setRequireApiKey(false);

        LlmPlan plan = new LlmGateway(properties, mapper).generate("分析该事故");

        assertEquals("RowKey规则不一致", plan.getRootCauseHypothesis());
        assertEquals("统一生成规则", plan.getChangePlan().get(0));

        JsonNode init = initRequest.get();
        assertEquals("app-1", init.path("appId").asText());
        assertEquals("tr-1", init.path("trCode").asText());
        assertEquals("v1", init.path("trVersion").asText());
        assertEquals("agent-1", init.path("agent_id").asText());
        assertTrue(init.path("timestamp").isNumber());
        assertFalse(init.path("requestId").asText().isEmpty());
        assertTrue(init.path("data").isObject());
        assertEquals(0, init.path("data").size());

        assertTrue(chatRequest.get().path("data").isObject());
        JsonNode chatData = chatRequest.get().path("data");
        assertEquals("session-test-1", chatData.path("session_id").asText());
        assertEquals("分析该事故", chatData.path("txt").asText());
        assertTrue(chatData.path("files").isArray());
        assertTrue(chatData.path("stream").asBoolean());
    }

    private java.util.Map<String, String> singleContent(String content) {
        java.util.Map<String, String> value = new java.util.LinkedHashMap<String, String>();
        value.put("content", content);
        return value;
    }

    private void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
