package com.example.aidevops.api;

import com.example.aidevops.model.DemoResult;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.runner.DemoOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DemoOrchestrator orchestrator;

    @Test
    void analyzesIncidentContextFromJsonBody() throws Exception {
        DemoResult result = result("DIAGNOSED");
        when(orchestrator.analyze(any(IncidentContext.class))).thenReturn(result);

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incident_id\":\"INC-API-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-API-1"))
                .andExpect(jsonPath("$.status").value("DIAGNOSED"));

        verify(orchestrator).analyze(any(IncidentContext.class));
    }

    @Test
    void triggersPullRequestFlowWithDryRunQueryParameter() throws Exception {
        DemoResult result = result("DRY_RUN_COMPLETE");
        when(orchestrator.generatePullRequest(any(IncidentContext.class), eq(Boolean.TRUE))).thenReturn(result);

        mockMvc.perform(post("/api/v1/incidents/pull-requests?dryRun=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incident_id\":\"INC-API-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRY_RUN_COMPLETE"));

        verify(orchestrator).generatePullRequest(any(IncidentContext.class), eq(Boolean.TRUE));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private DemoResult result(String status) {
        DemoResult result = new DemoResult();
        result.setTaskId("task-1");
        result.setIncidentId("INC-API-1");
        result.setStatus(status);
        return result;
    }
}
