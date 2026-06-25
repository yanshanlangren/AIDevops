package com.example.aidevops.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmPlan {
    @JsonProperty("root_cause_hypothesis")
    private String rootCauseHypothesis;
    private Double confidence;
    @JsonProperty("change_plan")
    private List<String> changePlan = new ArrayList<String>();
    @JsonProperty("target_files")
    private List<String> targetFiles = new ArrayList<String>();
    @JsonProperty("test_plan")
    private List<String> testPlan = new ArrayList<String>();
    @JsonProperty("risk_notice")
    private List<String> riskNotice = new ArrayList<String>();
    @JsonProperty("forbidden_actions_confirmed")
    private List<String> forbiddenActionsConfirmed = new ArrayList<String>();
    @JsonProperty("unified_diff")
    private String unifiedDiff;

    public String getRootCauseHypothesis() {
        return rootCauseHypothesis;
    }

    public void setRootCauseHypothesis(String rootCauseHypothesis) {
        this.rootCauseHypothesis = rootCauseHypothesis;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public List<String> getChangePlan() {
        return changePlan;
    }

    public void setChangePlan(List<String> changePlan) {
        this.changePlan = changePlan;
    }

    public List<String> getTargetFiles() {
        return targetFiles;
    }

    public void setTargetFiles(List<String> targetFiles) {
        this.targetFiles = targetFiles;
    }

    public List<String> getTestPlan() {
        return testPlan;
    }

    public void setTestPlan(List<String> testPlan) {
        this.testPlan = testPlan;
    }

    public List<String> getRiskNotice() {
        return riskNotice;
    }

    public void setRiskNotice(List<String> riskNotice) {
        this.riskNotice = riskNotice;
    }

    public List<String> getForbiddenActionsConfirmed() {
        return forbiddenActionsConfirmed;
    }

    public void setForbiddenActionsConfirmed(List<String> forbiddenActionsConfirmed) {
        this.forbiddenActionsConfirmed = forbiddenActionsConfirmed;
    }

    public String getUnifiedDiff() {
        return unifiedDiff;
    }

    public void setUnifiedDiff(String unifiedDiff) {
        this.unifiedDiff = unifiedDiff;
    }
}
