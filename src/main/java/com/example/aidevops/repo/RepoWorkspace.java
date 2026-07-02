package com.example.aidevops.repo;

import java.nio.file.Path;

public class RepoWorkspace implements AutoCloseable {
    private final Path directory;
    private final String gitCommand;
    private String branch;

    public RepoWorkspace(Path directory, String gitCommand) {
        this.directory = directory;
        this.gitCommand = gitCommand;
    }

    public Path getDirectory() {
        return directory;
    }

    public String getGitCommand() {
        return gitCommand;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public void close() {
        // Native Git commands do not keep repository resources open.
    }
}
