package com.example.aidevops.model;

public class CandidateFile {
    private String path;
    private String reason;
    private int score;
    private String content;

    public CandidateFile() {
    }

    public CandidateFile(String path, String reason, int score, String content) {
        this.path = path;
        this.reason = reason;
        this.score = score;
        this.content = content;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
