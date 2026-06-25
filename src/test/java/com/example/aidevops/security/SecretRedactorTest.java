package com.example.aidevops.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretRedactorTest {

    @Test
    void redactsSensitiveFieldsAndKnownTokenPatterns() throws Exception {
        SecretRedactor redactor = new SecretRedactor();
        JsonNode input = new ObjectMapper().readTree(
                "{\"apiToken\":\"secret-value\",\"message\":\"Bearer abcdefghijklmnop\"}");

        JsonNode result = redactor.redact(input);

        assertEquals("[REDACTED]", result.path("apiToken").asText());
        assertFalse(result.path("message").asText().contains("abcdefghijklmnop"));
        assertTrue(redactor.containsSecret("sk-abcdefghijklmnop1234"));
    }
}
