package com.example.aidevops.repo;

import com.example.aidevops.config.GithubProperties;
import com.example.aidevops.config.RepoProperties;
import com.example.aidevops.github.GithubAuthentication;
import com.example.aidevops.model.IncidentContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RepoManager {
    private static final Logger log = LoggerFactory.getLogger(RepoManager.class);

    private final RepoProperties repo;
    private final GithubProperties github;
    private final GithubAuthentication authentication;

    public RepoManager(RepoProperties repo, GithubProperties github, GithubAuthentication authentication) {
        this.repo = repo;
        this.github = github;
        this.authentication = authentication;
    }

    public RepoWorkspace prepare(IncidentContext incident, String taskId) {
        Path directory = Paths.get(repo.getLocalBaseDir()).toAbsolutePath().normalize()
                .resolve(repo.getTargetRepoName() + "-" + safe(incident.getIncidentId()) + "-" + safe(taskId));
        log.info("Preparing repository workspace: incidentId={}, taskId={}, repository={}, baseBranch={}, directory={}",
                incident.getIncidentId(), taskId, repo.getTargetRepoName(), repo.getDefaultBranch(), directory);
        deleteRecursively(directory);
        try {
            Files.createDirectories(directory.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create repository workspace parent " + directory.getParent(), e);
        }
        validateCloneUrl();

        runGit(directory.getParent(), GitAuthentication.OPTIONAL, Arrays.asList(
                "clone", "--branch", repo.getDefaultBranch(), "--single-branch", "--",
                repo.getCloneUrl(), directory.toString()));
        log.info("Repository cloned: incidentId={}, taskId={}, directory={}",
                incident.getIncidentId(), taskId, directory);

        RepoWorkspace workspace = new RepoWorkspace(directory, repo.getGitCommand());
        String branch = branchName(incident);
        runGit(directory, GitAuthentication.NONE, Arrays.asList("checkout", "-b", branch));
        workspace.setBranch(branch);
        log.info("Working branch created: incidentId={}, taskId={}, branch={}",
                incident.getIncidentId(), taskId, branch);
        return workspace;
    }

    public String commit(RepoWorkspace workspace, IncidentContext incident, List<String> approvedFiles) {
        if (approvedFiles == null || approvedFiles.isEmpty()) {
            throw new IllegalArgumentException("No policy-approved files are available for commit");
        }
        List<String> addArguments = new ArrayList<String>();
        addArguments.add("add");
        addArguments.add("--");
        addArguments.addAll(approvedFiles);
        runGit(workspace.getDirectory(), GitAuthentication.NONE, addArguments);

        runGit(workspace.getDirectory(), GitAuthentication.NONE, Arrays.asList(
                "-c", "user.name=" + github.getUserName(),
                "-c", "user.email=" + github.getUserEmail(),
                "commit", "-m", "[AI-DEVOPS][" + incident.getIncidentId() + "] Prepare automated fix"));
        String commitId = runGit(workspace.getDirectory(), GitAuthentication.NONE,
                Arrays.asList("rev-parse", "HEAD")).getStdout().trim();
        if (!StringUtils.hasText(commitId)) {
            throw new IllegalStateException("Native Git returned an empty commit id");
        }
        log.info("Repository changes committed: incidentId={}, branch={}, commit={}, files={}",
                incident.getIncidentId(), workspace.getBranch(), commitId, approvedFiles.size());
        return commitId;
    }

    public void push(RepoWorkspace workspace) {
        log.info("Pushing repository branch: branch={}", workspace.getBranch());
        runGit(workspace.getDirectory(), GitAuthentication.REQUIRED, Arrays.asList(
                "push", "--no-verify", "origin", "refs/heads/" + workspace.getBranch()
                        + ":refs/heads/" + workspace.getBranch()));
        log.info("Repository branch pushed: branch={}", workspace.getBranch());
    }

    private GitCommandResult runGit(
            Path directory, GitAuthentication gitAuthentication, List<String> arguments) {
        List<String> command = new ArrayList<String>();
        command.add(repo.getGitCommand());
        command.addAll(arguments);
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(directory.toFile());
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            if (gitAuthentication != GitAuthentication.NONE) {
                authentication.configureGit(builder, gitAuthentication == GitAuthentication.REQUIRED);
            }
            process = builder.start();
            StreamCollector stdout = new StreamCollector(process.getInputStream());
            StreamCollector stderr = new StreamCollector(process.getErrorStream());
            Thread stdoutThread = new Thread(stdout, "native-git-stdout");
            Thread stderrThread = new Thread(stderr, "native-git-stderr");
            stdoutThread.start();
            stderrThread.start();
            boolean finished = process.waitFor(repo.getGitTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Native Git command timed out after "
                        + repo.getGitTimeoutSeconds() + " seconds: " + commandName(arguments));
            }
            stdoutThread.join(1000L);
            stderrThread.join(1000L);
            GitCommandResult result = new GitCommandResult(
                    process.exitValue(), stdout.getContent(), stderr.getContent());
            if (result.getExitCode() != 0) {
                String detail = StringUtils.hasText(result.getStderr())
                        ? result.getStderr().trim() : result.getStdout().trim();
                throw new IllegalStateException("Native Git command failed: command="
                        + commandName(arguments) + ", exitCode=" + result.getExitCode() + ", detail=" + detail);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot execute native Git command " + repo.getGitCommand(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            throw new IllegalStateException("Native Git command was interrupted", e);
        }
    }

    private String commandName(List<String> arguments) {
        return arguments == null || arguments.isEmpty() ? "git" : "git " + arguments.get(0);
    }

    private void validateCloneUrl() {
        if (!StringUtils.hasText(repo.getGitCommand())) {
            throw new IllegalStateException("repo.git-command must be configured");
        }
        if (repo.getGitTimeoutSeconds() <= 0) {
            throw new IllegalStateException("repo.git-timeout-seconds must be greater than zero");
        }
        if (!StringUtils.hasText(repo.getCloneUrl())) {
            throw new IllegalStateException("repo.clone-url must be configured for a remote GitHub repository");
        }
        if (repo.getCloneUrl().contains("github.com/OWNER/REPO")) {
            throw new IllegalStateException("Configure repo.clone-url in application.yml before running the demo");
        }
    }

    private String branchName(IncidentContext incident) {
        String fingerprint = safe(incident.getErrorFingerprint());
        if (fingerprint.length() > 40) {
            fingerprint = fingerprint.substring(0, 40);
        }
        return repo.getWorkingBranchPrefix() + "/" + safe(incident.getIncidentId()) + "/" + fingerprint;
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot clean repository workspace " + directory, e);
        }
    }

    private static class GitCommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        private GitCommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        private int getExitCode() {
            return exitCode;
        }

        private String getStdout() {
            return stdout;
        }

        private String getStderr() {
            return stderr;
        }
    }

    private enum GitAuthentication {
        NONE,
        OPTIONAL,
        REQUIRED
    }

    private static class StreamCollector implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private StreamCollector(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[4096];
            int read;
            try {
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                // The process exit code and stderr carry the actionable failure.
            }
        }

        private String getContent() {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
