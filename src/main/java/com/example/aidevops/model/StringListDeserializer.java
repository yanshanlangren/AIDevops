package com.example.aidevops.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Accepts the model's preferred string-array format and tolerates a single
 * string value as a one-element list.
 */
public class StringListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        List<String> values = new ArrayList<String>();
        JsonToken token = parser.currentToken();

        if (token == JsonToken.START_ARRAY) {
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.VALUE_NULL) {
                    continue;
                }
                if (parser.currentToken() != JsonToken.VALUE_STRING) {
                    throw JsonMappingException.from(parser, "Expected a string value in the string array");
                }
                addIfPresent(values, parser.getValueAsString());
            }
            return values;
        }

        if (token == JsonToken.VALUE_NULL) {
            return values;
        }

        if (token == JsonToken.VALUE_STRING) {
            addIfPresent(values, parser.getValueAsString());
            return values;
        }

        throw JsonMappingException.from(parser, "Expected a string or an array of strings");
    }

    @Override
    public List<String> getNullValue(DeserializationContext context) {
        return new ArrayList<String>();
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null && !value.trim().isEmpty()) {
            values.add(value.trim());
        }
    }
}
