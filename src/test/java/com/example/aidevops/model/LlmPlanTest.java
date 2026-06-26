package com.example.aidevops.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPlanTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void convertsStringChangePlanToSingleElementList() throws Exception {
        LlmPlan plan = mapper.readValue(
                "{\"root_cause_hypothesis\":\"cause\",\"change_plan\":\"modify validation\"}",
                LlmPlan.class);

        assertEquals(1, plan.getChangePlan().size());
        assertEquals("modify validation", plan.getChangePlan().get(0));
    }

    @Test
    void keepsChangePlanArray() throws Exception {
        LlmPlan plan = mapper.readValue(
                "{\"root_cause_hypothesis\":\"cause\",\"change_plan\":[\"first\",\"second\"]}",
                LlmPlan.class);

        assertEquals(2, plan.getChangePlan().size());
        assertEquals("first", plan.getChangePlan().get(0));
        assertEquals("second", plan.getChangePlan().get(1));
    }

    @Test
    void convertsAllSupportedStringFieldsToSingleElementLists() throws Exception {
        LlmPlan plan = mapper.readValue(
                "{"
                        + "\"root_cause_hypothesis\":\"cause\","
                        + "\"change_plan\":\"change\","
                        + "\"test_plan\":\"test\","
                        + "\"target_files\":\"src/main/java/Demo.java\","
                        + "\"risk_notes\":\"risk\","
                        + "\"validation_steps\":\"validate\","
                        + "\"forbidden_actions\":\"no auto merge\""
                        + "}",
                LlmPlan.class);

        assertEquals("change", plan.getChangePlan().get(0));
        assertEquals("test", plan.getTestPlan().get(0));
        assertEquals("src/main/java/Demo.java", plan.getTargetFiles().get(0));
        assertEquals("risk", plan.getRiskNotice().get(0));
        assertEquals("validate", plan.getValidationSteps().get(0));
        assertEquals("no auto merge", plan.getForbiddenActions().get(0));
    }

    @Test
    void keepsAllSupportedArrayFields() throws Exception {
        LlmPlan plan = mapper.readValue(
                "{"
                        + "\"root_cause_hypothesis\":\"cause\","
                        + "\"change_plan\":[\"change-1\",\"change-2\"],"
                        + "\"test_plan\":[\"test-1\",\"test-2\"],"
                        + "\"target_files\":[\"A.java\",\"B.java\"],"
                        + "\"risk_notes\":[\"risk-1\",\"risk-2\"],"
                        + "\"validation_steps\":[\"validate-1\",\"validate-2\"],"
                        + "\"forbidden_actions\":[\"no production release\",\"no auto merge\"]"
                        + "}",
                LlmPlan.class);

        assertEquals(2, plan.getChangePlan().size());
        assertEquals(2, plan.getTestPlan().size());
        assertEquals(2, plan.getTargetFiles().size());
        assertEquals(2, plan.getRiskNotice().size());
        assertEquals(2, plan.getValidationSteps().size());
        assertEquals(2, plan.getForbiddenActions().size());
    }

    @Test
    void acceptsLegacyForbiddenActionsConfirmedField() throws Exception {
        LlmPlan plan = mapper.readValue(
                "{\"forbidden_actions_confirmed\":\"no production release\"}",
                LlmPlan.class);

        assertEquals(1, plan.getForbiddenActions().size());
        assertEquals("no production release", plan.getForbiddenActions().get(0));
    }

    @Test
    void convertsNullListFieldsToEmptyLists() throws Exception {
        LlmPlan plan = mapper.readValue(
                "{"
                        + "\"change_plan\":null,"
                        + "\"target_files\":null,"
                        + "\"test_plan\":null,"
                        + "\"risk_notes\":null,"
                        + "\"validation_steps\":null,"
                        + "\"forbidden_actions\":null"
                        + "}",
                LlmPlan.class);

        assertEquals(0, plan.getChangePlan().size());
        assertEquals(0, plan.getTargetFiles().size());
        assertEquals(0, plan.getTestPlan().size());
        assertEquals(0, plan.getRiskNotice().size());
        assertEquals(0, plan.getValidationSteps().size());
        assertEquals(0, plan.getForbiddenActions().size());
    }

    @Test
    void serializesCanonicalListFieldNames() throws Exception {
        LlmPlan plan = new LlmPlan();
        plan.setRiskNotice(Arrays.asList("review risk"));
        plan.setForbiddenActions(Arrays.asList("no auto merge"));

        String json = mapper.writeValueAsString(plan);

        assertTrue(json.contains("\"risk_notes\":[\"review risk\"]"));
        assertTrue(json.contains("\"forbidden_actions\":[\"no auto merge\"]"));
        assertFalse(json.contains("risk_notice"));
        assertFalse(json.contains("forbidden_actions_confirmed"));
    }

    @Test
    void acceptsStructuredFileEditsAndNewFiles() throws Exception {
        LlmPlan plan = mapper.readValue(
                "{"
                        + "\"file_edits\":[{"
                        + "\"path\":\"src/main/java/demo/A.java\","
                        + "\"old_text\":\"old\","
                        + "\"new_text\":\"new\""
                        + "}],"
                        + "\"new_files\":[{"
                        + "\"path\":\"src/test/java/demo/ATest.java\","
                        + "\"content\":\"test\""
                        + "}]"
                        + "}",
                LlmPlan.class);

        assertTrue(plan.hasStructuredEdits());
        assertEquals("src/main/java/demo/A.java", plan.getFileEdits().get(0).getPath());
        assertEquals("old", plan.getFileEdits().get(0).getOldText());
        assertEquals("new", plan.getFileEdits().get(0).getNewText());
        assertEquals("src/test/java/demo/ATest.java", plan.getNewFiles().get(0).getPath());
        assertEquals("test", plan.getNewFiles().get(0).getContent());
    }
}
