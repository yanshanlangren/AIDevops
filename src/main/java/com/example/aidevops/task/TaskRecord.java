package com.example.aidevops.task;

import com.example.aidevops.model.DemoResult;
import java.time.Instant;

public class TaskRecord {
    private final String taskId;
    private final String incidentId;
    private final TaskType type;
    private final Instant submittedAt;
    private volatile TaskStatus status;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile String message;
    private volatile DemoResult result;
    private volatile TaskError error;

    public TaskRecord(String taskId, String incidentId, TaskType type) {
        this.taskId = taskId;
        this.incidentId = incidentId;
        this.type = type;
        this.submittedAt = Instant.now();
        this.status = TaskStatus.QUEUED;
        this.message = "Task accepted and queued";
    }

    public String getTaskId() {
        return taskId;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public TaskType getType() {
        return type;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getMessage() {
        return message;
    }

    public DemoResult getResult() {
        return result;
    }

    public TaskError getError() {
        return error;
    }

    public void markRunning() {
        status = TaskStatus.RUNNING;
        startedAt = Instant.now();
        message = "Task is running";
    }

    public void markSucceeded(DemoResult value) {
        result = value;
        status = TaskStatus.SUCCEEDED;
        completedAt = Instant.now();
        message = "Task completed";
    }

    public void markFailed(Throwable throwable) {
        error = new TaskError(
                throwable.getClass().getSimpleName(),
                throwable.getMessage() == null ? "Task execution failed" : throwable.getMessage());
        status = TaskStatus.FAILED;
        completedAt = Instant.now();
        message = "Task failed";
    }
}
