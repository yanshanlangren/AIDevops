package com.example.aidevops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "execution")
public class ExecutionProperties {
    private String buildCommand;
    private String testCommand;
    private String staticCheckCommand;
    private int timeoutSeconds = 300;

    public String getBuildCommand() {
        return buildCommand;
    }

    public void setBuildCommand(String buildCommand) {
        this.buildCommand = buildCommand;
    }

    public String getTestCommand() {
        return testCommand;
    }

    public void setTestCommand(String testCommand) {
        this.testCommand = testCommand;
    }

    public String getStaticCheckCommand() {
        return staticCheckCommand;
    }

    public void setStaticCheckCommand(String staticCheckCommand) {
        this.staticCheckCommand = staticCheckCommand;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
