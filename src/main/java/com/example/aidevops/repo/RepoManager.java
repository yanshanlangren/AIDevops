package com.example.aidevops.repo;

import com.example.aidevops.config.GithubProperties;
import com.example.aidevops.config.RepoProperties;
import com.example.aidevops.model.IncidentContext;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RepoManager {
    private static final Logger log = LoggerFactory.getLogger(RepoManager.class);

    private final RepoProperties repo;
    private final GithubProperties github;

    public RepoManager(RepoProperties repo, GithubProperties github) {
        this.repo = repo;
        this.github = github;
    }

    public RepoWorkspace prepare(IncidentContext incident, String taskId) {
        Path directory = Paths.get(repo.getLocalBaseDir()).toAbsolutePath().normalize()
                .resolve(repo.getTargetRepoName() + "-" + safe(incident.getIncidentId()) + "-" + safe(taskId));
        log.info("Preparing repository workspace: incidentId={}, taskId={}, repository={}, baseBranch={}, directory={}",
                incident.getIncidentId(), taskId, repo.getTargetRepoName(), repo.getDefaultBranch(), directory);
        deleteRecursively(directory);
        try {
            Files.createDirectories(directory.getParent());
            if (!StringUtils.hasText(repo.getCloneUrl())) {
                throw new IllegalStateException("repo.clone-url must be configured for a remote GitHub repository");
            }
            if (repo.getCloneUrl().contains("github.com/OWNER/REPO")) {
                throw new IllegalStateException(
                        "Configure repo.clone-url in application.yml before running the demo");
            }
            Git git = cloneRepository(directory);
            log.info("Repository cloned: incidentId={}, taskId={}, directory={}",
                    incident.getIncidentId(), taskId, directory);
            RepoWorkspace workspace = new RepoWorkspace(directory, git);
            String branch = branchName(incident);
            git.checkout().setCreateBranch(true).setName(branch).call();
            workspace.setBranch(branch);
            log.info("Working branch created: incidentId={}, taskId={}, branch={}",
                    incident.getIncidentId(), taskId, branch);
            return workspace;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare repository workspace " + directory, e);
        } catch (GitAPIException e) {
            throw new IllegalStateException("Git operation failed while preparing " + directory, e);
        }
    }

    public String commit(RepoWorkspace workspace, IncidentContext incident, List<String> approvedFiles) {
        try {
            if (approvedFiles == null || approvedFiles.isEmpty()) {
                throw new IllegalArgumentException("No policy-approved files are available for commit");
            }
            for (String file : approvedFiles) {
                workspace.getGit().add().addFilepattern(file).call();
            }
            ObjectId id = workspace.getGit().commit()
                    .setMessage("[AI-DEVOPS][" + incident.getIncidentId() + "] Prepare automated fix")
                    .setAuthor(github.getUserName(), github.getUserEmail())
                    .call()
                    .getId();
            log.info("Repository changes committed: incidentId={}, branch={}, commit={}, files={}",
                    incident.getIncidentId(), workspace.getBranch(), id.name(), approvedFiles.size());
            return id.name();
        } catch (GitAPIException e) {
            throw new IllegalStateException("Cannot commit generated change", e);
        }
    }

    public void push(RepoWorkspace workspace) {
        String token = System.getenv(github.getTokenEnv());
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Missing GitHub token environment variable: " + github.getTokenEnv());
        }
        try {
            log.info("Pushing repository branch: branch={}", workspace.getBranch());
            workspace.getGit().push()
                    .setRemote("origin")
                    .setCredentialsProvider(credentials(token))
                    .setRefSpecs(new RefSpec("refs/heads/" + workspace.getBranch()
                            + ":refs/heads/" + workspace.getBranch()))
                    .call();
            log.info("Repository branch pushed: branch={}", workspace.getBranch());
        } catch (GitAPIException e) {
            throw new IllegalStateException("Cannot push branch " + workspace.getBranch(), e);
        }
    }

    private Git cloneRepository(Path directory) throws GitAPIException {
        String token = System.getenv(github.getTokenEnv());
        org.eclipse.jgit.api.CloneCommand command = Git.cloneRepository()
                .setURI(repo.getCloneUrl())
                .setDirectory(directory.toFile())
                .setBranch(repo.getDefaultBranch())
                .setBranchesToClone(Collections.singletonList("refs/heads/" + repo.getDefaultBranch()));
        if (StringUtils.hasText(token)) {
            command.setCredentialsProvider(credentials(token));
        }
        return command.call();
    }

    private CredentialsProvider credentials(String token) {
        return new UsernamePasswordCredentialsProvider("x-access-token", token);
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
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot clean repository workspace " + directory, e);
        }
    }
}
