package com.example.aidevops.llm;

import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.model.LlmPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (!"openai-compatible".equalsIgnoreCase(model.getProvider())) {
            log.error("Unsupported model provider configured: provider={}", model.getProvider());
            throw new IllegalStateException("Unsupported model provider: " + model.getProvider());
        }
        return invokeOpenAiCompatible(prompt);
    }

    private LlmPlan invokeOpenAiCompatible(String prompt) {
        String key = System.getenv(model.getApiKeyEnv());
        if (!StringUtils.hasText(key)) {
            log.error("Model API key environment variable is missing: environmentVariable={}",
                    model.getApiKeyEnv());
            throw new IllegalStateException("Missing model API key environment variable: " + model.getApiKeyEnv());
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(model.getTimeoutSeconds() * 1000);
        factory.setReadTimeout(model.getTimeoutSeconds() * 1000);
        RestTemplate rest = new RestTemplate(factory);

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
