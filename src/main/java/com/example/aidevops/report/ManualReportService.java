package com.example.aidevops.report;

import com.example.aidevops.audit.AuditSession;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.LlmPlan;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ManualReportService {

    public void write(AuditSession audit, IncidentContext incident, String stage, List<String> reasons, LlmPlan plan) {
        StringBuilder report = new StringBuilder();
        report.append("# 人工分析报告\n\n");
        report.append("- IncidentId: ").append(incident.getIncidentId()).append('\n');
        report.append("- Service: ").append(incident.getServiceName()).append('\n');
        report.append("- Problem Type: ").append(incident.getProblemType()).append('\n');
        report.append("- Risk Level: ").append(incident.getRiskLevel()).append('\n');
        report.append("- Downgrade Stage: ").append(stage).append("\n\n");
        report.append("## 无法自动创建 PR 的原因\n\n");
        if (reasons == null || reasons.isEmpty()) {
            report.append("- 未提供明确原因，请检查审计文件。\n");
        } else {
            for (String reason : reasons) {
                report.append("- ").append(reason).append('\n');
            }
        }
        if (plan != null) {
            report.append("\n## 根因假设\n\n").append(plan.getRootCauseHypothesis()).append("\n\n");
            report.append("## 建议变更计划\n\n");
            for (String item : plan.getChangePlan()) {
                report.append("- ").append(item).append('\n');
            }
            report.append("\n## 风险提示\n\n");
            for (String item : plan.getRiskNotice()) {
                report.append("- ").append(item).append('\n');
            }
        }
        report.append("\n## 建议人工处理\n\n");
        report.append("核对 IncidentContext、相关代码、复现测试和门禁失败结果后，由研发人员决定是否修改。\n");
        audit.writeText("manual_analysis_report.md", report.toString());
    }
}
