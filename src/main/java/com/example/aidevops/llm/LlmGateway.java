package com.example.aidevops.llm;

import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.model.LlmPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmGateway {
    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final ModelProperties model;
    private final ObjectMapper mapper;

    public LlmGateway(ModelProperties model, ObjectMapper mapper) {
        this.model = model;
        this.mapper = mapper;
    }

    public LlmPlan generate(String prompt) {
        if ("openai-compatible".equalsIgnoreCase(model.getProvider())) {
            return invokeOpenAiCompatible(prompt);
        }
        if ("chatabc".equalsIgnoreCase(model.getProvider())) {
            return invokeChatAbc(prompt);
        }
        log.error("Unsupported model provider configured: provider={}", model.getProvider());
        throw new IllegalStateException("Unsupported model provider: " + model.getProvider());
    }

    private LlmPlan invokeOpenAiCompatible(String prompt) {
        String key = readApiKey(true);
        RestTemplate rest = createRestTemplate();

        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("model", model.getModelName());
        request.put("temperature", model.getTemperature());
        request.put("max_tokens", model.getMaxTokens());
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        Map<String, String> message = new LinkedHashMap<String, String>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        request.put("messages", messages);
        Map<String, String> responseFormat = new LinkedHashMap<String, String>();
        responseFormat.put("type", "json_object");
        request.put("response_format", responseFormat);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(key);
        String url = stripTrailingSlash(model.getApiBaseUrl()) + "/chat/completions";
        log.info("Model request started: provider={}, model={}, endpoint={}, maxAttempts={}",
                model.getProvider(), model.getModelName(), url, model.getMaxAttempts());
        RuntimeException last = null;
        for (int attempt = 1; attempt <= model.getMaxAttempts(); attempt++) {
            try {
                ResponseEntity<JsonNode> response = rest.exchange(
                        url, HttpMethod.POST, new HttpEntity<Map<String, Object>>(request, headers), JsonNode.class);
                JsonNode root = response.getBody();
                if (root == null || !root.path("choices").isArray() || root.path("choices").size() == 0) {
                    throw new IllegalStateException("Model response contains no choices");
                }
                String content = root.path("choices").get(0).path("message").path("content").asText();
                log.info("Model request succeeded: model={}, attempt={}", model.getModelName(), attempt);
                return parsePlan(content);
            } catch (RuntimeException e) {
                last = e;
                log.warn("Model request attempt failed: model={}, attempt={}, maxAttempts={}, message={}",
                        model.getModelName(), attempt, model.getMaxAttempts(), e.getMessage(), e);
                if (attempt < model.getMaxAttempts()) {
                    sleep(500L * attempt);
                }
            }
        }
        log.error("Model request failed after retries: model={}, maxAttempts={}",
                model.getModelName(), model.getMaxAttempts(), last);
        throw new IllegalStateException("Model request failed after retries", last);
    }

    private LlmPlan invokeChatAbc(String prompt) {
        ModelProperties.ChatAbc chatabc = model.getChatabc();
        String key = chatabc.isRequireApiKey() ? readApiKey(true) : null;
        RestTemplate rest = createRestTemplate();
        String initUrl = endpoint(chatabc.getInitSessionPath());
        String chatUrl = endpoint(chatabc.getChatPath());
        log.info("Model request started: provider={}, initEndpoint={}, chatEndpoint={}, maxAttempts={}",
                model.getProvider(), initUrl, chatUrl, model.getMaxAttempts());

        RuntimeException last = null;
        for (int attempt = 1; attempt <= model.getMaxAttempts(); attempt++) {
            try {
                String sessionId = initializeChatAbcSession(rest, initUrl, key, chatabc);
                String content = chatWithChatAbc(rest, chatUrl, key, chatabc, sessionId, prompt);
                log.info("Model request succeeded: provider={}, attempt={}, sessionId={}",
                        model.getProvider(), attempt, sessionId);
                return parsePlan(content);
            } catch (RuntimeException e) {
                last = e;
                log.warn("Model request attempt failed: provider={}, attempt={}, maxAttempts={}, message={}",
                        model.getProvider(), attempt, model.getMaxAttempts(), e.getMessage(), e);
                if (attempt < model.getMaxAttempts()) {
                    sleep(500L * attempt);
                }
            }
        }
        log.error("Model request failed after retries: provider={}, maxAttempts={}",
                model.getProvider(), model.getMaxAttempts(), last);
        throw new IllegalStateException("Model request failed after retries", last);
    }

    private String initializeChatAbcSession(RestTemplate rest, String url, String key,
                                            ModelProperties.ChatAbc chatabc) {
        Map<String, Object> request = baseChatAbcRequest(chatabc);
        request.put("data", Collections.emptyMap());
        ResponseEntity<JsonNode> response = rest.exchange(url, HttpMethod.POST,
                new HttpEntity<Map<String, Object>>(request, chatAbcHeaders(key, false)), JsonNode.class);
        JsonNode root = response.getBody();
        if (root == null) {
            throw new IllegalStateException("ChatABC init_session returned an empty response");
        }
        String responseCode = firstText(root, "resCode", "rescode");
        if (StringUtils.hasText(chatabc.getSuccessCode()) && !chatabc.getSuccessCode().equals(responseCode)) {
            throw new IllegalStateException("ChatABC init_session failed: resCode=" + responseCode
                    + ", message=" + firstText(root, "resMessage", "message"));
        }
        String sessionId = root.path("data").path("session_id").asText();
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalStateException("ChatABC init_session response misses data.session_id");
        }
        log.info("ChatABC session initialized: sessionId={}", sessionId);
        return sessionId;
    }

    private String chatWithChatAbc(RestTemplate rest, String url, String key,
                                   ModelProperties.ChatAbc chatabc, String sessionId, String prompt) {
        Map<String, Object> request = baseChatAbcRequest(chatabc);
        Map<String, Object> chatData = new LinkedHashMap<String, Object>();
        chatData.put("session_id", sessionId);
        chatData.put("txt", prompt);
        chatData.put("files", Collections.emptyList());
        chatData.put("stream", chatabc.isStream());
        request.put("data", chatData);

        if (!chatabc.isStream()) {
            ResponseEntity<JsonNode> response = rest.exchange(url, HttpMethod.POST,
                    new HttpEntity<Map<String, Object>>(request, chatAbcHeaders(key, false)), JsonNode.class);
            return extractChatAbcJsonContent(response.getBody(), chatabc.getSuccessCode());
        }

        return rest.execute(url, HttpMethod.POST, httpRequest -> {
            HttpHeaders headers = httpRequest.getHeaders();
            headers.putAll(chatAbcHeaders(key, true));
            mapper.writeValue(httpRequest.getBody(), request);
        }, response -> {
            try {
                return new ChatAbcSseParser(mapper).parse(response.getBody(), chatabc.getSuccessCode());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read ChatABC stream", e);
            }
        });
    }

    private Map<String, Object> baseChatAbcRequest(ModelProperties.ChatAbc chatabc) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("appId", chatabc.getAppId());
        request.put("trCode", chatabc.getTrCode());
        request.put("trVersion", chatabc.getTrVersion());
        request.put("timestamp", System.currentTimeMillis());
        request.put("agent_id", chatabc.getAgentId());
        request.put("requestId", UUID.randomUUID().toString());
        return request;
    }

    private String extractChatAbcJsonContent(JsonNode root, String successCode) {
        if (root == null) {
            throw new IllegalStateException("ChatABC chat returned an empty response");
        }
        String responseCode = firstText(root, "resCode", "rescode");
        if (StringUtils.hasText(successCode) && StringUtils.hasText(responseCode)
                && !successCode.equals(responseCode)) {
            throw new IllegalStateException("ChatABC chat failed: resCode=" + responseCode);
        }
        JsonNode data = root.path("data");
        if (data.isArray() && data.size() > 0) {
            data = data.get(0);
        }
        String content = firstText(data, "content", "txt");
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("ChatABC chat response contains no model content");
        }
        return content;
    }

    private HttpHeaders chatAbcHeaders(String key, boolean stream) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(stream ? MediaType.TEXT_EVENT_STREAM : MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(key)) {
            headers.setBearerAuth(key);
        }
        return headers;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(model.getTimeoutSeconds() * 1000);
        factory.setReadTimeout(model.getTimeoutSeconds() * 1000);
        return new RestTemplate(factory);
    }

    private String readApiKey(boolean required) {
        String key = StringUtils.hasText(model.getApiKeyEnv()) ? System.getenv(model.getApiKeyEnv()) : null;
        if (required && !StringUtils.hasText(key)) {
            log.error("Model API key environment variable is missing: environmentVariable={}",
                    model.getApiKeyEnv());
            throw new IllegalStateException("Missing model API key environment variable: " + model.getApiKeyEnv());
        }
        return key;
    }

    private String endpoint(String path) {
        String base = stripTrailingSlash(model.getApiBaseUrl());
        if (!StringUtils.hasText(path)) {
            return base;
        }
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    private String firstText(JsonNode node, String first, String second) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.path(first).asText();
        return StringUtils.hasText(value) ? value : node.path(second).asText();
    }

    private LlmPlan parsePlan(String content) {
        String json = content == null ? "" : content.trim();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            LlmPlan plan = mapper.readValue(json, LlmPlan.class);
            if (!StringUtils.hasText(plan.getRootCauseHypothesis())) {
                throw new IllegalStateException("Model output misses root_cause_hypothesis");
            }
            return plan;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Model output is not valid structured JSON", e);
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying model request", e);
        }
    }
}
