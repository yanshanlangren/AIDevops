package com.example.aidevops.llm;

import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.RetrievalResult;
import com.example.aidevops.security.SecretRedactor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void requiresAllListFieldsToBeStringArrays() {
        PromptBuilder builder = new PromptBuilder(
                new ObjectMapper(), new ModelProperties(), new SecretRedactor());

        String prompt = builder.build(new IncidentContext(), new RetrievalResult());

        assertTrue(prompt.contains(
                "change_plan, target_files, test_plan, risk_notes, validation_steps, and "
                        + "forbidden_actions MUST always be JSON arrays of strings"));
        assertTrue(prompt.contains(
                "[\"no production release\", \"no auto merge\", \"no production config edit\"]"));
        assertTrue(prompt.contains("Never return a bare string for these fields."));
    }
}
