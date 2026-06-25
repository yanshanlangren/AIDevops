package com.example.aidevops.model;

public class TestResult {
    private String command;
    private boolean success;
    private boolean skipped;
    private boolean timedOut;
    private long durationMs;
    private int exitCode;
    private String stdoutFile;
    private String stderrFile;
    private String failureReason;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public String getStdoutFile() {
        return stdoutFile;
    }

    public void setStdoutFile(String stdoutFile) {
        this.stdoutFile = stdoutFile;
    }

    public String getStderrFile() {
        return stderrFile;
    }

    public void setStderrFile(String stderrFile) {
        this.stderrFile = stderrFile;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
