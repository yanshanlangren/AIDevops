package com.example.aidevops.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SecretRedactor {
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i).*(token|password|passwd|secret|cookie|session|private.?key|authorization|api.?key|card.?number|id.?card).*");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(gh[pousr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{16,}|bearer\\s+[A-Za-z0-9._-]{12,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)");

    public JsonNode redact(JsonNode source) {
        if (source == null) {
            return null;
        }
        JsonNode copy = source.deepCopy();
        redactNode(copy);
        return copy;
    }

    public String redactText(String text) {
        if (text == null) {
            return null;
        }
        return SECRET_VALUE.matcher(text).replaceAll("[REDACTED_SECRET]");
    }

    public boolean containsSecret(String text) {
        return text != null && SECRET_VALUE.matcher(text).find();
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_FIELD.matcher(field.getKey()).matches()) {
                    object.put(field.getKey(), "[REDACTED]");
                } else if (field.getValue().isTextual()) {
                    object.put(field.getKey(), redactText(field.getValue().asText()));
                } else {
                    redactNode(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode value = array.get(i);
                if (value.isTextual()) {
                    array.set(i, array.textNode(redactText(value.asText())));
                } else {
                    redactNode(value);
                }
            }
        }
    }
}
