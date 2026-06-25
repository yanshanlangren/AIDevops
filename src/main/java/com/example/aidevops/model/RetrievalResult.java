package com.example.aidevops.model;

import java.util.ArrayList;
import java.util.List;

public class RetrievalResult {
    private List<CandidateFile> targetFiles = new ArrayList<CandidateFile>();
    private List<CandidateFile> ignoredFiles = new ArrayList<CandidateFile>();

    public List<CandidateFile> getTargetFiles() {
        return targetFiles;
    }

    public void setTargetFiles(List<CandidateFile> targetFiles) {
        this.targetFiles = targetFiles;
    }

    public List<CandidateFile> getIgnoredFiles() {
        return ignoredFiles;
    }

    public void setIgnoredFiles(List<CandidateFile> ignoredFiles) {
        this.ignoredFiles = ignoredFiles;
    }
}
