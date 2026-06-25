package com.example.aidevops.repo;

import java.nio.file.Path;
import org.eclipse.jgit.api.Git;

public class RepoWorkspace implements AutoCloseable {
    private final Path directory;
    private final Git git;
    private String branch;

    public RepoWorkspace(Path directory, Git git) {
        this.directory = directory;
        this.git = git;
    }

    public Path getDirectory() {
        return directory;
    }

    public Git getGit() {
        return git;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public void close() {
        git.close();
    }
}
