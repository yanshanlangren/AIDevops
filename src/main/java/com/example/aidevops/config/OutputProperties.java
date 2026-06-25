package com.example.aidevops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "output")
public class OutputProperties {
    private boolean createPullRequest = true;
    private boolean writeManualReportOnFailure = true;

    public boolean isCreatePullRequest() {
        return createPullRequest;
    }

    public void setCreatePullRequest(boolean createPullRequest) {
        this.createPullRequest = createPullRequest;
    }

    public boolean isWriteManualReportOnFailure() {
        return writeManualReportOnFailure;
    }

    public void setWriteManualReportOnFailure(boolean writeManualReportOnFailure) {
        this.writeManualReportOnFailure = writeManualReportOnFailure;
    }
}
