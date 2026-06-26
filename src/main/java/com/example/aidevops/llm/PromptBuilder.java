package com.example.aidevops.llm;

import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.model.CandidateFile;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.LlmPlan;
import com.example.aidevops.model.RetrievalResult;
import com.example.aidevops.security.SecretRedactor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class PromptBuilder {
    private final ObjectMapper mapper;
    private final ModelProperties model;
    private final SecretRedactor redactor;

    public PromptBuilder(ObjectMapper mapper, ModelProperties model, SecretRedactor redactor) {
        this.mapper = mapper;
        this.model = model;
        this.redactor = redactor;
    }

    public String build(IncidentContext incident, RetrievalResult retrieval) {
        try {
            JsonNode redactedIncident = redactor.redact(mapper.valueToTree(incident));
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是一个受控的 DevOps 软件工程智能体。\n");
            prompt.append("只能基于已提供的事故证据和代码片段进行分析，并生成最小化 Java 变更。\n");
            prompt.append("禁止提出生产发布、自动合并、生产配置修改、权限/认证/风控逻辑修改、");
            prompt.append("密钥暴露或绕过测试的建议。\n");
            prompt.append("只返回一个 JSON 对象，不要返回 Markdown 或额外说明。该 JSON 必须包含字段：root_cause_hypothesis, confidence, ");
            prompt.append("change_plan, target_files, test_plan, risk_notes, validation_steps, ");
            prompt.append("forbidden_actions；同时应包含 file_edits/new_files 或 unified_diff。\n");
            prompt.append("字段 change_plan、target_files、test_plan、risk_notes、validation_steps 和 ");
            prompt.append("forbidden_actions 必须始终是 JSON 字符串数组，即使只有一个元素也不能返回裸字符串。\n");
            prompt.append("forbidden_actions 必须精确包含以下确认项：");
            prompt.append("[\"no production release\", \"no auto merge\", \"no production config edit\"].\n");
            prompt.append("优先使用结构化补丁格式，不要首选直接手写原始 diff：\n");
            prompt.append("- file_edits 是数组，每个元素格式为 {\"path\":\"...\",\"old_text\":\"文件中精确存在的原文\",");
            prompt.append("\"new_text\":\"替换后的文本\"}。\n");
            prompt.append("- new_files 是数组，每个元素格式为 {\"path\":\"...\",\"content\":\"新文件完整内容\"}。\n");
            prompt.append("- old_text 必须在对应仓库文件中精确匹配且只能匹配一个位置。\n");
            prompt.append("如果无法使用结构化补丁格式，再返回 unified_diff。\n");
            appendUnifiedDiffRules(prompt);
            prompt.append('\n');
            prompt.append("事故上下文 IncidentContext：\n");
            prompt.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(redactedIncident));
            prompt.append("\n\n候选代码片段（已带行号；无关代码已省略）：\n");
            for (CandidateFile file : retrieval.getTargetFiles()) {
                prompt.append("\n--- 文件：").append(file.getPath()).append(" ---\n");
                prompt.append(redactor.redactText(file.getContent())).append('\n');
                if (prompt.length() >= model.getMaxInputChars()) {
                    break;
                }
            }
            if (prompt.length() > model.getMaxInputChars()) {
                return prompt.substring(0, model.getMaxInputChars());
            }
            return prompt.toString();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot build model prompt", e);
        }
    }

    public String buildPatchRepairPrompt(IncidentContext incident,
                                         RetrievalResult retrieval,
                                         LlmPlan previousPlan,
                                         String applyError) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("你正在修复一个由受控 DevOps 智能体生成的非法补丁。\n");
            prompt.append("不要改变根因结论、目标文件、策略确认项或业务修复意图；只修复补丁格式和上下文匹配问题。\n");
            prompt.append("上一次 unified_diff 执行 git apply --check 失败，错误如下：\n");
            prompt.append(applyError == null ? "未知 git apply 错误" : applyError).append("\n\n");
            prompt.append("只返回一个 JSON 对象，字段结构与上次保持一致。");
            prompt.append("所有列表字段都必须是 JSON 字符串数组。");
            prompt.append("修正后的 unified_diff 必须是合法补丁，并且能够应用到下面提供的仓库代码片段。\n");
            appendUnifiedDiffRules(prompt);
            prompt.append("\n上一次模型输出：\n");
            prompt.append(mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(redactor.redact(mapper.valueToTree(previousPlan))));
            prompt.append("\n\nIncidentContext 摘要：\n");
            prompt.append(mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(redactor.redact(mapper.valueToTree(incident))));
            prompt.append("\n\n候选代码片段：\n");
            for (CandidateFile file : retrieval.getTargetFiles()) {
                prompt.append("\n--- 文件：").append(file.getPath()).append(" ---\n");
                prompt.append(redactor.redactText(file.getContent())).append('\n');
                if (prompt.length() >= model.getMaxInputChars()) {
                    break;
                }
            }
            if (prompt.length() > model.getMaxInputChars()) {
                return prompt.substring(0, model.getMaxInputChars());
            }
            return prompt.toString();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot build patch repair prompt", e);
        }
    }

    private void appendUnifiedDiffRules(StringBuilder prompt) {
        prompt.append("unified_diff 规则：\n");
        prompt.append("- 必须是相对于仓库根目录的合法 git unified diff，并且必须包含测试变更。\n");
        prompt.append("- 每个变更文件都必须以 diff --git a/path b/path 开头。\n");
        prompt.append("- 修改已有文件时必须包含 --- a/path 和 +++ b/path。\n");
        prompt.append("- 新增文件时必须包含 new file mode 100644、--- /dev/null 和 +++ b/path。\n");
        prompt.append("- @@ -a,b +c,d @@ 这类 hunk 头中的行数必须与后续 hunk 内容严格一致。\n");
        prompt.append("- 不要使用 Markdown 代码块包裹 diff，不要省略 diff --git 头。\n");
    }
}
