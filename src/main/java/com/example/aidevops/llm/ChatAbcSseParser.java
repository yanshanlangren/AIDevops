package com.example.aidevops.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

final class ChatAbcSseParser {
    private static final Logger log = LoggerFactory.getLogger(ChatAbcSseParser.class);

    private final ObjectMapper mapper;
    private final boolean logResponses;
    private final String sessionId;
    private final PrintStream chunkOutput;
    private boolean chunkOutputOpen;

    ChatAbcSseParser(ObjectMapper mapper) {
        this(mapper, false, "");
    }

    ChatAbcSseParser(ObjectMapper mapper, boolean logResponses) {
        this(mapper, logResponses, "");
    }

    ChatAbcSseParser(ObjectMapper mapper, boolean logResponses, String sessionId) {
        this(mapper, logResponses, sessionId, System.out);
    }

    ChatAbcSseParser(ObjectMapper mapper, boolean logResponses, String sessionId, PrintStream chunkOutput) {
        this.mapper = mapper;
        this.logResponses = logResponses;
        this.sessionId = sessionId;
        this.chunkOutput = chunkOutput;
    }

    String parse(InputStream input, String successCode) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder chunkContent = new StringBuilder();
        StringBuilder messageContent = new StringBuilder();
        String event = null;
        StringBuilder data = new StringBuilder();
        boolean done = false;

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    logEvent(event, data.toString());
                    boolean eventDone = consumeEvent(
                            event, data.toString(), successCode, chunkContent, messageContent);
                    event = null;
                    data.setLength(0);
                    if (eventDone) {
                        done = true;
                        break;
                    }
                    continue;
                }
                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).trim());
                }
            }
            if (event != null || data.length() > 0) {
                logEvent(event, data.toString());
                done = consumeEvent(
                        event, data.toString(), successCode, chunkContent, messageContent) || done;
            }
        } finally {
            finishChunkOutput();
        }
        if (!done) {
            throw new IllegalStateException("ChatABC stream ended without a successful done event");
        }
        String result = messageContent.length() > 0 ? messageContent.toString() : chunkContent.toString();
        if (!StringUtils.hasText(result)) {
            throw new IllegalStateException("ChatABC stream contains no model content");
        }
        if (logResponses) {
            log.info("ChatABC final model content: {}", result);
        }
        return result;
    }

    private void logEvent(String event, String data) {
        if ("chunk".equalsIgnoreCase(event)) {
            return;
        }
        finishChunkOutput();
        if (logResponses && (StringUtils.hasText(event) || StringUtils.hasText(data))) {
            log.info("ChatABC SSE response: sessionId={}, event={}, data={}",
                    sessionId, event, singleLine(data));
        }
    }

    private boolean consumeEvent(String event, String data, String successCode,
                                 StringBuilder chunkContent, StringBuilder messageContent) throws IOException {
        if ("chat_started".equalsIgnoreCase(event)) {
            return false;
        }
        if (!StringUtils.hasText(data)) {
            return false;
        }
        JsonNode payload = mapper.readTree(data);
        if ("chunk".equalsIgnoreCase(event)) {
            appendContent(payload, chunkContent);
            printReasoning(payload);
            return false;
        }
        if ("message".equalsIgnoreCase(event)) {
            messageContent.setLength(0);
            appendContent(payload, messageContent);
            return false;
        }
        if ("done".equalsIgnoreCase(event)) {
            String status = payload.path("status").asText();
            String responseCode = firstText(payload, "rescode", "resCode");
            if (!"success".equalsIgnoreCase(status)
                    || (StringUtils.hasText(successCode) && !successCode.equals(responseCode))) {
                throw new IllegalStateException("ChatABC returned unsuccessful done event: status="
                        + status + ", resCode=" + responseCode);
            }
            return true;
        }
        return false;
    }

    private void appendContent(JsonNode payload, StringBuilder target) {
        JsonNode content = payload.path("content");
        if (!content.isMissingNode() && !content.isNull()) {
            target.append(content.asText());
        }
    }

    private void printReasoning(JsonNode payload) {
        if (!logResponses) {
            return;
        }
        JsonNode value = payload.path("additional_kwargs").path("reasoning");
        if (value.isMissingNode() || value.isNull() || !StringUtils.hasText(value.asText())) {
            return;
        }
        String fragment = value.asText().replace('\r', ' ').replace('\n', ' ');
        chunkOutput.print(fragment);
        chunkOutput.flush();
        chunkOutputOpen = true;
    }

    private void finishChunkOutput() {
        if (chunkOutputOpen) {
            chunkOutput.println();
            chunkOutput.flush();
            chunkOutputOpen = false;
        }
    }

    private String singleLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = node.path(first).asText();
        return StringUtils.hasText(value) ? value : node.path(second).asText();
    }
}
