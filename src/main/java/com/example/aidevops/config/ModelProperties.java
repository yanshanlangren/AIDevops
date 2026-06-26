package com.example.aidevops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "model")
public class ModelProperties {
    private String provider = "openai-compatible";
    private String apiBaseUrl;
    private String apiKeyEnv = "LLM_API_KEY";
    private String modelName;
    private double temperature = 0.1;
    private int maxTokens = 6000;
    private int timeoutSeconds = 120;
    private int maxAttempts = 2;
    private int maxInputChars = 120000;
    private int maxFileChars = 20000;
    private int maxFiles = 20;
    private int snippetContextLines = 60;
    private int maxSnippetsPerFile = 3;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public void setApiKeyEnv(String apiKeyEnv) {
        this.apiKeyEnv = apiKeyEnv;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    public int getMaxFileChars() {
        return maxFileChars;
    }

    public void setMaxFileChars(int maxFileChars) {
        this.maxFileChars = maxFileChars;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public int getSnippetContextLines() {
        return snippetContextLines;
    }

    public void setSnippetContextLines(int snippetContextLines) {
        this.snippetContextLines = snippetContextLines;
    }

    public int getMaxSnippetsPerFile() {
        return maxSnippetsPerFile;
    }

    public void setMaxSnippetsPerFile(int maxSnippetsPerFile) {
        this.maxSnippetsPerFile = maxSnippetsPerFile;
    }
}
