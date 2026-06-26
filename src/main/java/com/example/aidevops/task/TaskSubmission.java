package com.example.aidevops.task;

public class TaskSubmission {
    private final String taskId;
    private final String incidentId;
    private final TaskStatus status;
    private final String statusUrl;

    public TaskSubmission(String taskId, String incidentId, TaskStatus status, String statusUrl) {
        this.taskId = taskId;
        this.incidentId = incidentId;
        this.status = status;
        this.statusUrl = statusUrl;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getStatusUrl() {
        return statusUrl;
    }
}
