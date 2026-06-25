package com.example.aidevops.model;

import java.util.ArrayList;
import java.util.List;

public class AdmissionResult {
    private boolean admitted;
    private String level;
    private Double confidence;
    private String riskLevel;
    private List<String> reasons = new ArrayList<String>();
    private List<String> blockedReasons = new ArrayList<String>();

    public boolean isAdmitted() {
        return admitted;
    }

    public void setAdmitted(boolean admitted) {
        this.admitted = admitted;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public List<String> getBlockedReasons() {
        return blockedReasons;
    }

    public void setBlockedReasons(List<String> blockedReasons) {
        this.blockedReasons = blockedReasons;
    }
}
