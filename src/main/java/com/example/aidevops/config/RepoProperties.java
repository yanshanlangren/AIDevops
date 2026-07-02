package com.example.aidevops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "repo")
public class RepoProperties {
    private String localBaseDir;
    private String targetRepoName;
    private String cloneUrl;
    private String defaultBranch = "main";
    private String workingBranchPrefix = "ai-devops";
    private String gitCommand = "git";
    private int gitTimeoutSeconds = 120;

    public String getLocalBaseDir() {
        return localBaseDir;
    }

    public void setLocalBaseDir(String localBaseDir) {
        this.localBaseDir = localBaseDir;
    }

    public String getTargetRepoName() {
        return targetRepoName;
    }

    public void setTargetRepoName(String targetRepoName) {
        this.targetRepoName = targetRepoName;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public void setCloneUrl(String cloneUrl) {
        this.cloneUrl = cloneUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getWorkingBranchPrefix() {
        return workingBranchPrefix;
    }

    public void setWorkingBranchPrefix(String workingBranchPrefix) {
        this.workingBranchPrefix = workingBranchPrefix;
    }

    public String getGitCommand() {
        return gitCommand;
    }

    public void setGitCommand(String gitCommand) {
        this.gitCommand = gitCommand;
    }

    public int getGitTimeoutSeconds() {
        return gitTimeoutSeconds;
    }

    public void setGitTimeoutSeconds(int gitTimeoutSeconds) {
        this.gitTimeoutSeconds = gitTimeoutSeconds;
    }
}
