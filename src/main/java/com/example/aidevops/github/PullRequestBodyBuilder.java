package com.example.aidevops.github;

import com.example.aidevops.audit.AuditSession;
import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.LlmPlan;
import com.example.aidevops.model.PatchValidationResult;
import com.example.aidevops.model.TestResult;
import com.example.aidevops.model.VerificationResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PullRequestBodyBuilder {
    private final ModelProperties model;

    public PullRequestBodyBuilder(ModelProperties model) {
        this.model = model;
    }

    public String build(IncidentContext incident, LlmPlan plan, PatchValidationResult patch,
                        VerificationResult verification, AuditSession audit) {
        StringBuilder body = new StringBuilder();
        body.append("## 事故摘要\n\n");
        body.append("- IncidentId: ").append(incident.getIncidentId()).append('\n');
        body.append("- Service: ").append(incident.getServiceName()).append('\n');
        body.append("- Error Fingerprint: ").append(incident.getErrorFingerprint()).append('\n');
        body.append("- Affected Version: ").append(incident.getAffectedVersion()).append('\n');
        body.append("- Risk Level: ").append(incident.getRiskLevel()).append('\n');
        body.append("- Confidence: ").append(plan.getConfidence()).append("\n\n");

        body.append("## 根因假设\n\n").append(plan.getRootCauseHypothesis()).append("\n\n");
        appendList(body, "## 修改范围", patch.getChangedFiles());
        appendList(body, "## 变更说明", plan.getChangePlan());
        appendList(body, "## 测试说明", plan.getTestPlan());

        body.append("## 本地验证结果\n\n");
        body.append("| 验证项 | 结果 |\n|---|---|\n");
        body.append("| 编译 | ").append(display(verification.getBuild())).append(" |\n");
        body.append("| 单元测试 | ").append(display(verification.getTest())).append(" |\n");
        body.append("| 静态检查 | ").append(display(verification.getStaticCheck())).append(" |\n");
        body.append("| Diff 行数 | ").append(patch.getDiffLines()).append(" |\n\n");

        appendList(body, "## 风险边界", plan.getRiskNotice());
        body.append("## 固定控制边界\n\n");
        body.append("- 不自动合并\n- 不自动发布\n- 不修改生产配置\n");
        body.append("- 不修改权限过滤、认证、风控或敏感字段展示逻辑\n\n");
        body.append("## 人工 Review 关注点\n\n");
        body.append("- 变更是否符合实际业务规则\n");
        body.append("- RowKey 生成规则是否与预处理阶段一致\n");
        body.append("- 历史字段兼容与回归测试是否充分\n");
        body.append("- 是否影响分页、排序或权限过滤\n\n");
        body.append("## 审计信息\n\n");
        body.append("- AuditId: ").append(incident.getAuditId()).append('\n');
        body.append("- Model: ").append(model.getModelName()).append('\n');
        body.append("- Audit Log Path: `").append(audit.getDirectory()).append("`\n");
        return body.toString();
    }

    private void appendList(StringBuilder body, String heading, List<String> values) {
        body.append(heading).append("\n\n");
        if (values != null) {
            for (String value : values) {
                body.append("- ").append(value).append('\n');
            }
        }
        body.append('\n');
    }

    private String display(TestResult result) {
        if (result == null || result.isSkipped()) {
            return "未配置";
        }
        return result.isSuccess() ? "通过" : "未通过";
    }
}
