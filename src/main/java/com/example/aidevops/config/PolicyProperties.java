package com.example.aidevops.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "policy")
public class PolicyProperties {
    private double minConfidence = 0.75;
    private List<String> allowedRiskLevels = new ArrayList<String>();
    private List<String> allowedProblemTypes = new ArrayList<String>();
    private List<String> forbiddenProblemTypes = new ArrayList<String>();
    private List<String> pathWhitelist = new ArrayList<String>();
    private List<String> pathBlacklist = new ArrayList<String>();
    private int maxChangedFiles = 8;
    private int maxDiffLines = 500;
    private boolean requireTests = true;
    private String testPathPrefix = "src/test/java/";
    private boolean blockSecretsInDiff = true;
    private boolean blockProductionConfigEdit = true;

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public List<String> getAllowedRiskLevels() {
        return allowedRiskLevels;
    }

    public void setAllowedRiskLevels(List<String> allowedRiskLevels) {
        this.allowedRiskLevels = allowedRiskLevels;
    }

    public List<String> getAllowedProblemTypes() {
        return allowedProblemTypes;
    }

    public void setAllowedProblemTypes(List<String> allowedProblemTypes) {
        this.allowedProblemTypes = allowedProblemTypes;
    }

    public List<String> getForbiddenProblemTypes() {
        return forbiddenProblemTypes;
    }

    public void setForbiddenProblemTypes(List<String> forbiddenProblemTypes) {
        this.forbiddenProblemTypes = forbiddenProblemTypes;
    }

    public List<String> getPathWhitelist() {
        return pathWhitelist;
    }

    public void setPathWhitelist(List<String> pathWhitelist) {
        this.pathWhitelist = pathWhitelist;
    }

    public List<String> getPathBlacklist() {
        return pathBlacklist;
    }

    public void setPathBlacklist(List<String> pathBlacklist) {
        this.pathBlacklist = pathBlacklist;
    }

    public int getMaxChangedFiles() {
        return maxChangedFiles;
    }

    public void setMaxChangedFiles(int maxChangedFiles) {
        this.maxChangedFiles = maxChangedFiles;
    }

    public int getMaxDiffLines() {
        return maxDiffLines;
    }

    public void setMaxDiffLines(int maxDiffLines) {
        this.maxDiffLines = maxDiffLines;
    }

    public boolean isRequireTests() {
        return requireTests;
    }

    public void setRequireTests(boolean requireTests) {
        this.requireTests = requireTests;
    }

    public String getTestPathPrefix() {
        return testPathPrefix;
    }

    public void setTestPathPrefix(String testPathPrefix) {
        this.testPathPrefix = testPathPrefix;
    }

    public boolean isBlockSecretsInDiff() {
        return blockSecretsInDiff;
    }

    public void setBlockSecretsInDiff(boolean blockSecretsInDiff) {
        this.blockSecretsInDiff = blockSecretsInDiff;
    }

    public boolean isBlockProductionConfigEdit() {
        return blockProductionConfigEdit;
    }

    public void setBlockProductionConfigEdit(boolean blockProductionConfigEdit) {
        this.blockProductionConfigEdit = blockProductionConfigEdit;
    }
}
