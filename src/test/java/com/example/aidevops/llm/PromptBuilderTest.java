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
                "字段 change_plan、target_files、test_plan、risk_notes、validation_steps 和 "
                        + "forbidden_actions 必须始终是 JSON 字符串数组"));
        assertTrue(prompt.contains(
                "[\"no production release\", \"no auto merge\", \"no production config edit\"]"));
        assertTrue(prompt.contains("不能返回裸字符串"));
        assertTrue(prompt.contains("优先使用结构化补丁格式"));
        assertTrue(prompt.contains("每个变更文件都必须以 diff --git a/path b/path 开头。"));
        assertTrue(prompt.contains("@@ -a,b +c,d @@ 这类 hunk 头中的行数必须与后续 hunk 内容严格一致"));
    }
}
