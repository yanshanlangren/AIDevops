package com.example.aidevops.model;

public class VerificationResult {
    private boolean success;
    private TestResult build;
    private TestResult test;
    private TestResult staticCheck;
    private String failureReason;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public TestResult getBuild() {
        return build;
    }

    public void setBuild(TestResult build) {
        this.build = build;
    }

    public TestResult getTest() {
        return test;
    }

    public void setTest(TestResult test) {
        this.test = test;
    }

    public TestResult getStaticCheck() {
        return staticCheck;
    }

    public void setStaticCheck(TestResult staticCheck) {
        this.staticCheck = staticCheck;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
