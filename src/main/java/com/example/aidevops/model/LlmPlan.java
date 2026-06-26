package com.example.aidevops.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmPlan {
    @JsonProperty("root_cause_hypothesis")
    private String rootCauseHypothesis;
    private Double confidence;
    @JsonProperty("change_plan")
    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> changePlan = new ArrayList<String>();
    @JsonProperty("target_files")
    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> targetFiles = new ArrayList<String>();
    @JsonProperty("test_plan")
    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> testPlan = new ArrayList<String>();
    @JsonProperty("risk_notes")
    @JsonAlias("risk_notice")
    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> riskNotice = new ArrayList<String>();
    @JsonProperty("validation_steps")
    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> validationSteps = new ArrayList<String>();
    @JsonProperty("forbidden_actions")
    @JsonAlias("forbidden_actions_confirmed")
    @JsonDeserialize(using = StringListDeserializer.class)
    private List<String> forbiddenActions = new ArrayList<String>();
    @JsonProperty("unified_diff")
    private String unifiedDiff;
    @JsonProperty("file_edits")
    private List<FileEdit> fileEdits = new ArrayList<FileEdit>();
    @JsonProperty("new_files")
    private List<NewFile> newFiles = new ArrayList<NewFile>();

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

    public List<String> getValidationSteps() {
        return validationSteps;
    }

    public void setValidationSteps(List<String> validationSteps) {
        this.validationSteps = validationSteps;
    }

    public List<String> getForbiddenActions() {
        return forbiddenActions;
    }

    public void setForbiddenActions(List<String> forbiddenActions) {
        this.forbiddenActions = forbiddenActions;
    }

    public String getUnifiedDiff() {
        return unifiedDiff;
    }

    public void setUnifiedDiff(String unifiedDiff) {
        this.unifiedDiff = unifiedDiff;
    }

    public List<FileEdit> getFileEdits() {
        return fileEdits;
    }

    public void setFileEdits(List<FileEdit> fileEdits) {
        this.fileEdits = fileEdits;
    }

    public List<NewFile> getNewFiles() {
        return newFiles;
    }

    public void setNewFiles(List<NewFile> newFiles) {
        this.newFiles = newFiles;
    }

    public boolean hasStructuredEdits() {
        return (fileEdits != null && !fileEdits.isEmpty())
                || (newFiles != null && !newFiles.isEmpty());
    }
}
