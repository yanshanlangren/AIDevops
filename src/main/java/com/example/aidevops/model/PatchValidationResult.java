package com.example.aidevops.model;

import java.util.ArrayList;
import java.util.List;

public class PatchValidationResult {
    private boolean valid;
    private List<String> changedFiles = new ArrayList<String>();
    private int diffLines;
    private List<String> blockedReasons = new ArrayList<String>();

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(List<String> changedFiles) {
        this.changedFiles = changedFiles;
    }

    public int getDiffLines() {
        return diffLines;
    }

    public void setDiffLines(int diffLines) {
        this.diffLines = diffLines;
    }

    public List<String> getBlockedReasons() {
        return blockedReasons;
    }

    public void setBlockedReasons(List<String> blockedReasons) {
        this.blockedReasons = blockedReasons;
    }
}
