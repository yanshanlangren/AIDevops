package com.example.aidevops.task;

import com.example.aidevops.model.DemoResult;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.runner.DemoOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentTaskServiceTest {

    @Test
    void storesSuccessfulTaskResult() {
        DemoOrchestrator orchestrator = mock(DemoOrchestrator.class);
        DemoResult result = new DemoResult();
        result.setStatus("DIAGNOSED");
        when(orchestrator.analyze(any(IncidentContext.class), anyString())).thenReturn(result);
        IncidentTaskService service = new IncidentTaskService(orchestrator, new SyncTaskExecutor());

        TaskSubmission submission = service.submitAnalysis(incident());
        TaskRecord task = service.get(submission.getTaskId());

        assertEquals(TaskStatus.SUCCEEDED, task.getStatus());
        assertEquals("DIAGNOSED", task.getResult().getStatus());
    }

    @Test
    void storesFailureWithoutExposingStackTraceInTaskResult() {
        DemoOrchestrator orchestrator = mock(DemoOrchestrator.class);
        when(orchestrator.analyze(any(IncidentContext.class), anyString()))
                .thenThrow(new IllegalStateException("model timed out"));
        IncidentTaskService service = new IncidentTaskService(orchestrator, new SyncTaskExecutor());

        TaskSubmission submission = service.submitAnalysis(incident());
        TaskRecord task = service.get(submission.getTaskId());

        assertEquals(TaskStatus.FAILED, task.getStatus());
        assertNotNull(task.getError());
        assertEquals("model timed out", task.getError().getMessage());
    }

    private IncidentContext incident() {
        IncidentContext incident = new IncidentContext();
        incident.setIncidentId("INC-TASK-1");
        return incident;
    }
}
