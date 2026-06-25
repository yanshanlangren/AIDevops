package com.example.aidevops.policy;

import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.model.AdmissionResult;
import com.example.aidevops.model.IncidentContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdmissionService {
    private final PolicyProperties policy;

    public AdmissionService(PolicyProperties policy) {
        this.policy = policy;
    }

    public AdmissionResult evaluate(IncidentContext incident) {
        AdmissionResult result = new AdmissionResult();
        result.setConfidence(incident.getConfidence());
        result.setRiskLevel(incident.getRiskLevel());

        require(result, "incident_id", incident.getIncidentId());
        require(result, "system_name", incident.getSystemName());
        require(result, "service_name", incident.getServiceName());
        require(result, "error_fingerprint", incident.getErrorFingerprint());
        require(result, "log_summary", incident.getLogSummary());
        require(result, "recent_commits", incident.getRecentCommits());
        require(result, "scenario_context", incident.getScenarioContext());
        require(result, "problem_type", incident.getProblemType());
        require(result, "risk_level", incident.getRiskLevel());
        require(result, "confidence", incident.getConfidence());
        require(result, "auto_pr_allowed", incident.getAutoPrAllowed());
        require(result, "production_release_allowed", incident.getProductionReleaseAllowed());

        if (incident.getConfidence() != null && incident.getConfidence() < policy.getMinConfidence()) {
            result.getBlockedReasons().add("confidence below threshold " + policy.getMinConfidence());
        } else if (incident.getConfidence() != null) {
            result.getReasons().add("confidence above threshold");
        }
        if (!containsIgnoreCase(policy.getAllowedRiskLevels(), incident.getRiskLevel())) {
            result.getBlockedReasons().add("risk level is not allowed: " + incident.getRiskLevel());
        } else {
            result.getReasons().add("risk level is allowed");
        }
        if (!Boolean.TRUE.equals(incident.getAutoPrAllowed())) {
            result.getBlockedReasons().add("auto_pr_allowed must be true");
        } else {
            result.getReasons().add("automatic PR is explicitly allowed");
        }
        if (!Boolean.FALSE.equals(incident.getProductionReleaseAllowed())) {
            result.getBlockedReasons().add("production_release_allowed must be false");
        } else {
            result.getReasons().add("production release is disabled");
        }
        if (!containsIgnoreCase(policy.getAllowedProblemTypes(), incident.getProblemType())) {
            result.getBlockedReasons().add("problem type is not in L3 allowlist: " + incident.getProblemType());
        } else {
            result.getReasons().add("problem type is in L3 allowlist");
        }
        if (containsIgnoreCase(policy.getForbiddenProblemTypes(), incident.getProblemType())) {
            result.getBlockedReasons().add("problem type is explicitly forbidden: " + incident.getProblemType());
        }

        result.setAdmitted(result.getBlockedReasons().isEmpty());
        result.setLevel(result.isAdmitted() ? "L3" : "L2");
        return result;
    }

    private void require(AdmissionResult result, String field, Object value) {
        boolean missing = value == null;
        if (value instanceof String) {
            missing = !StringUtils.hasText((String) value);
        } else if (value instanceof List) {
            missing = ((List<?>) value).isEmpty();
        }
        if (missing) {
            result.getBlockedReasons().add("required field is missing: " + field);
        }
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        if (candidate == null) {
            return false;
        }
        for (String value : values) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
