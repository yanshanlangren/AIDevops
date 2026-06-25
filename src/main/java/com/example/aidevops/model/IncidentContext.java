package com.example.aidevops.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IncidentContext {
    @JsonProperty("incident_id")
    private String incidentId;
    @JsonProperty("system_name")
    private String systemName;
    @JsonProperty("service_name")
    private String serviceName;
    private String environment;
    @JsonProperty("affected_version")
    private String affectedVersion;
    @JsonProperty("error_fingerprint")
    private String errorFingerprint;
    @JsonProperty("trigger_source")
    private List<String> triggerSource = new ArrayList<String>();
    @JsonProperty("first_seen_time")
    private String firstSeenTime;
    @JsonProperty("last_seen_time")
    private String lastSeenTime;
    @JsonProperty("trace_ids")
    private List<String> traceIds = new ArrayList<String>();
    @JsonProperty("log_summary")
    private JsonNode logSummary;
    @JsonProperty("metrics_snapshot")
    private JsonNode metricsSnapshot;
    @JsonProperty("runtime_context")
    private JsonNode runtimeContext;
    @JsonProperty("release_context")
    private JsonNode releaseContext;
    @JsonProperty("recent_commits")
    private List<RecentCommit> recentCommits = new ArrayList<RecentCommit>();
    @JsonProperty("dependency_context")
    private JsonNode dependencyContext;
    @JsonProperty("data_access_context")
    private JsonNode dataAccessContext;
    @JsonProperty("business_context")
    private JsonNode businessContext;
    @JsonProperty("scenario_context")
    private JsonNode scenarioContext;
    @JsonProperty("suspected_components")
    private List<String> suspectedComponents = new ArrayList<String>();
    @JsonProperty("suspected_type")
    private String suspectedType;
    @JsonProperty("problem_type")
    private String problemType;
    @JsonProperty("risk_level")
    private String riskLevel;
    private Double confidence;
    @JsonProperty("auto_pr_allowed")
    private Boolean autoPrAllowed;
    @JsonProperty("production_release_allowed")
    private Boolean productionReleaseAllowed;
    @JsonProperty("audit_id")
    private String auditId;

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getAffectedVersion() {
        return affectedVersion;
    }

    public void setAffectedVersion(String affectedVersion) {
        this.affectedVersion = affectedVersion;
    }

    public String getErrorFingerprint() {
        return errorFingerprint;
    }

    public void setErrorFingerprint(String errorFingerprint) {
        this.errorFingerprint = errorFingerprint;
    }

    public List<String> getTriggerSource() {
        return triggerSource;
    }

    public void setTriggerSource(List<String> triggerSource) {
        this.triggerSource = triggerSource;
    }

    public String getFirstSeenTime() {
        return firstSeenTime;
    }

    public void setFirstSeenTime(String firstSeenTime) {
        this.firstSeenTime = firstSeenTime;
    }

    public String getLastSeenTime() {
        return lastSeenTime;
    }

    public void setLastSeenTime(String lastSeenTime) {
        this.lastSeenTime = lastSeenTime;
    }

    public List<String> getTraceIds() {
        return traceIds;
    }

    public void setTraceIds(List<String> traceIds) {
        this.traceIds = traceIds;
    }

    public JsonNode getLogSummary() {
        return logSummary;
    }

    public void setLogSummary(JsonNode logSummary) {
        this.logSummary = logSummary;
    }

    public JsonNode getMetricsSnapshot() {
        return metricsSnapshot;
    }

    public void setMetricsSnapshot(JsonNode metricsSnapshot) {
        this.metricsSnapshot = metricsSnapshot;
    }

    public JsonNode getRuntimeContext() {
        return runtimeContext;
    }

    public void setRuntimeContext(JsonNode runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    public JsonNode getReleaseContext() {
        return releaseContext;
    }

    public void setReleaseContext(JsonNode releaseContext) {
        this.releaseContext = releaseContext;
    }

    public List<RecentCommit> getRecentCommits() {
        return recentCommits;
    }

    public void setRecentCommits(List<RecentCommit> recentCommits) {
        this.recentCommits = recentCommits;
    }

    public JsonNode getDependencyContext() {
        return dependencyContext;
    }

    public void setDependencyContext(JsonNode dependencyContext) {
        this.dependencyContext = dependencyContext;
    }

    public JsonNode getDataAccessContext() {
        return dataAccessContext;
    }

    public void setDataAccessContext(JsonNode dataAccessContext) {
        this.dataAccessContext = dataAccessContext;
    }

    public JsonNode getBusinessContext() {
        return businessContext;
    }

    public void setBusinessContext(JsonNode businessContext) {
        this.businessContext = businessContext;
    }

    public JsonNode getScenarioContext() {
        return scenarioContext;
    }

    public void setScenarioContext(JsonNode scenarioContext) {
        this.scenarioContext = scenarioContext;
    }

    public List<String> getSuspectedComponents() {
        return suspectedComponents;
    }

    public void setSuspectedComponents(List<String> suspectedComponents) {
        this.suspectedComponents = suspectedComponents;
    }

    public String getSuspectedType() {
        return suspectedType;
    }

    public void setSuspectedType(String suspectedType) {
        this.suspectedType = suspectedType;
    }

    public String getProblemType() {
        return problemType;
    }

    public void setProblemType(String problemType) {
        this.problemType = problemType;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Boolean getAutoPrAllowed() {
        return autoPrAllowed;
    }

    public void setAutoPrAllowed(Boolean autoPrAllowed) {
        this.autoPrAllowed = autoPrAllowed;
    }

    public Boolean getProductionReleaseAllowed() {
        return productionReleaseAllowed;
    }

    public void setProductionReleaseAllowed(Boolean productionReleaseAllowed) {
        this.productionReleaseAllowed = productionReleaseAllowed;
    }

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }
}
