package com.example.aidevops.policy;

import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.model.AdmissionResult;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.RecentCommit;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdmissionServiceTest {

    @Test
    void admitsLowRiskCompleteIncident() {
        AdmissionResult result = service().evaluate(validIncident());
        assertTrue(result.isAdmitted());
    }

    @Test
    void blocksProductionReleaseAndLowConfidence() {
        IncidentContext incident = validIncident();
        incident.setConfidence(0.60);
        incident.setProductionReleaseAllowed(Boolean.TRUE);

        AdmissionResult result = service().evaluate(incident);

        assertFalse(result.isAdmitted());
        assertTrue(result.getBlockedReasons().toString().contains("confidence below threshold"));
        assertTrue(result.getBlockedReasons().toString().contains("production_release_allowed"));
    }

    private AdmissionService service() {
        PolicyProperties policy = new PolicyProperties();
        policy.setMinConfidence(0.75);
        policy.setAllowedRiskLevels(Arrays.asList("low", "medium"));
        policy.setAllowedProblemTypes(Arrays.asList("query_condition_convert"));
        policy.setForbiddenProblemTypes(Arrays.asList("permission_filter"));
        return new AdmissionService(policy);
    }

    private IncidentContext validIncident() {
        ObjectMapper mapper = new ObjectMapper();
        IncidentContext incident = new IncidentContext();
        incident.setIncidentId("INC-1");
        incident.setSystemName("query");
        incident.setServiceName("query-service");
        incident.setErrorFingerprint("empty-result");
        incident.setLogSummary(mapper.createObjectNode().put("message", "empty"));
        RecentCommit commit = new RecentCommit();
        commit.setFiles(Arrays.asList("src/main/java/demo/Query.java"));
        incident.setRecentCommits(Arrays.asList(commit));
        incident.setScenarioContext(mapper.createObjectNode().put("scenario", "query"));
        incident.setProblemType("query_condition_convert");
        incident.setRiskLevel("medium");
        incident.setConfidence(0.80);
        incident.setAutoPrAllowed(Boolean.TRUE);
        incident.setProductionReleaseAllowed(Boolean.FALSE);
        return incident;
    }
}
