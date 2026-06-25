package com.example.aidevops.model;

public class DemoResult {
    private String taskId;
    private String incidentId;
    private String status;
    private String stage;
    private String message;
    private String auditDirectory;
    private String pullRequestUrl;
    private LlmPlan analysis;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAuditDirectory() {
        return auditDirectory;
    }

    public void setAuditDirectory(String auditDirectory) {
        this.auditDirectory = auditDirectory;
    }

    public String getPullRequestUrl() {
        return pullRequestUrl;
    }

    public void setPullRequestUrl(String pullRequestUrl) {
        this.pullRequestUrl = pullRequestUrl;
    }

    public LlmPlan getAnalysis() {
        return analysis;
    }

    public void setAnalysis(LlmPlan analysis) {
        this.analysis = analysis;
    }
}
