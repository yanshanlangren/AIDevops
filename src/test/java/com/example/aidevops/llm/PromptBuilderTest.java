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
                "[\"禁止生产发布\", \"禁止自动合并\", \"禁止修改生产配置\"]"));
        assertTrue(prompt.contains("不能返回裸字符串"));
        assertTrue(prompt.contains("所有思考过程和非代码输出必须使用简体中文"));
        assertTrue(prompt.contains("包括 reasoning、根因分析、变更计划、测试计划、风险说明、验证步骤和禁止操作说明"));
        assertTrue(prompt.contains("必须精确匹配的内容保持原样，不要翻译"));
        assertTrue(prompt.contains("优先使用结构化补丁格式"));
        assertTrue(prompt.contains("每个变更文件都必须以 diff --git a/path b/path 开头。"));
        assertTrue(prompt.contains("@@ -a,b +c,d @@ 这类 hunk 头中的行数必须与后续 hunk 内容严格一致"));
    }

    @Test
    void requiresChineseExplanationsForPatchRepairPrompt() {
        PromptBuilder builder = new PromptBuilder(
                new ObjectMapper(), new ModelProperties(), new SecretRedactor());

        String prompt = builder.buildPatchRepairPrompt(
                new IncidentContext(), new RetrievalResult(), new com.example.aidevops.model.LlmPlan(), "error");

        assertTrue(prompt.contains("所有思考过程和非代码输出必须使用简体中文"));
        assertTrue(prompt.contains("不得使用英文撰写解释性或提示性内容"));
    }
}
