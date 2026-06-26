package com.example.aidevops.api;

import com.example.aidevops.task.IncidentTaskService;
import com.example.aidevops.task.TaskNotFoundException;
import com.example.aidevops.task.TaskRecord;
import com.example.aidevops.task.TaskStatus;
import com.example.aidevops.task.TaskSubmission;
import com.example.aidevops.task.TaskType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({IncidentController.class, TaskController.class})
class IncidentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentTaskService taskService;

    @Test
    void submitsAnalysisAsAsynchronousTask() throws Exception {
        TaskSubmission submission = submission("task-analysis");
        when(taskService.submitAnalysis(any())).thenReturn(submission);

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incident_id\":\"INC-API-1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/tasks/task-analysis"))
                .andExpect(jsonPath("$.taskId").value("task-analysis"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(taskService).submitAnalysis(any());
    }

    @Test
    void submitsPullRequestAsAsynchronousTask() throws Exception {
        TaskSubmission submission = submission("task-pr");
        when(taskService.submitPullRequest(any(), eq(Boolean.TRUE))).thenReturn(submission);

        mockMvc.perform(post("/api/v1/incidents/pull-requests?dryRun=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incident_id\":\"INC-API-1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value("task-pr"))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(taskService).submitPullRequest(any(), eq(Boolean.TRUE));
    }

    @Test
    void returnsTaskStatus() throws Exception {
        TaskRecord task = new TaskRecord("task-1", "INC-API-1", TaskType.ANALYZE);
        task.markRunning();
        when(taskService.get("task-1")).thenReturn(task);

        mockMvc.perform(get("/api/v1/tasks/task-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void returnsNotFoundForUnknownTask() throws Exception {
        when(taskService.get("missing")).thenThrow(new TaskNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/tasks/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returnsInternalServerErrorWhenTaskSubmissionFails() throws Exception {
        when(taskService.submitAnalysis(any()))
                .thenThrow(new IllegalStateException("Incident task queue is full"));

        mockMvc.perform(post("/api/v1/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"incident_id\":\"INC-API-1\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Incident task queue is full"));
    }

    private TaskSubmission submission(String taskId) {
        return new TaskSubmission(
                taskId,
                "INC-API-1",
                TaskStatus.QUEUED,
                "/api/v1/tasks/" + taskId);
    }
}
