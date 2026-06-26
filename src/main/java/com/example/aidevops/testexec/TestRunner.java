package com.example.aidevops.testexec;

import com.example.aidevops.audit.AuditSession;
import com.example.aidevops.config.ExecutionProperties;
import com.example.aidevops.model.TestResult;
import com.example.aidevops.model.VerificationResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TestRunner {
    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    private final ExecutionProperties execution;

    public TestRunner(ExecutionProperties execution) {
        this.execution = execution;
    }

    public VerificationResult verify(Path repository, AuditSession audit) {
        VerificationResult verification = new VerificationResult();
        TestResult build = execute(execution.getBuildCommand(), repository, audit, "build");
        verification.setBuild(build);
        if (!build.isSuccess()) {
            return fail(verification, "build", build);
        }
        TestResult test = execute(execution.getTestCommand(), repository, audit, "test");
        verification.setTest(test);
        if (!test.isSuccess()) {
            return fail(verification, "test", test);
        }
        TestResult staticCheck = execute(execution.getStaticCheckCommand(), repository, audit, "static_check");
        verification.setStaticCheck(staticCheck);
        if (!staticCheck.isSuccess()) {
            return fail(verification, "static check", staticCheck);
        }
        verification.setSuccess(true);
        return verification;
    }

    private VerificationResult fail(VerificationResult verification, String stage, TestResult result) {
        verification.setSuccess(false);
        verification.setFailureReason(stage + " failed: " + result.getFailureReason());
        return verification;
    }

    private TestResult execute(String command, Path repository, AuditSession audit, String name) {
        TestResult result = new TestResult();
        result.setCommand(command);
        if (!StringUtils.hasText(command)) {
            result.setSkipped(true);
            result.setSuccess(true);
            log.info("Engineering command skipped: stage={}", name);
            return result;
        }

        Path stdout = audit.resolve(name + "_stdout.log");
        Path stderr = audit.resolve(name + "_stderr.log");
        long start = System.currentTimeMillis();
        Process process = null;
        try {
            log.info("Engineering command started: stage={}, command={}, directory={}, timeoutSeconds={}",
                    name, command, repository, execution.getTimeoutSeconds());
            ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-lc", command);
            builder.directory(repository.toFile());
            builder.redirectOutput(stdout.toFile());
            builder.redirectError(stderr.toFile());
            process = builder.start();
            boolean finished = process.waitFor(execution.getTimeoutSeconds(), TimeUnit.SECONDS);
            result.setDurationMs(System.currentTimeMillis() - start);
            result.setStdoutFile(stdout.toString());
            result.setStderrFile(stderr.toString());
            if (!finished) {
                result.setTimedOut(true);
                result.setSuccess(false);
                result.setExitCode(-1);
                result.setFailureReason("command timed out after " + execution.getTimeoutSeconds() + " seconds");
                process.destroyForcibly();
                log.error("Engineering command timed out: stage={}, command={}, durationMs={}",
                        name, command, result.getDurationMs());
                return result;
            }
            result.setExitCode(process.exitValue());
            result.setSuccess(process.exitValue() == 0);
            if (!result.isSuccess()) {
                result.setFailureReason("command exited with code " + process.exitValue());
                log.error("Engineering command failed: stage={}, command={}, exitCode={}, durationMs={}, stderrFile={}",
                        name, command, result.getExitCode(), result.getDurationMs(), result.getStderrFile());
            } else {
                log.info("Engineering command succeeded: stage={}, command={}, durationMs={}",
                        name, command, result.getDurationMs());
            }
            return result;
        } catch (IOException e) {
            log.error("Cannot execute engineering command: stage={}, command={}, directory={}",
                    name, command, repository, e);
            throw new IllegalStateException("Cannot execute test command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            log.error("Engineering command interrupted: stage={}, command={}", name, command, e);
            throw new IllegalStateException("Test execution interrupted", e);
        }
    }
}
