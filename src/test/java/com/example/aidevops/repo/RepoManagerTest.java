package com.example.aidevops.repo;

import com.example.aidevops.config.GithubProperties;
import com.example.aidevops.config.RepoProperties;
import com.example.aidevops.model.IncidentContext;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoManagerTest {

    @Test
    void clonesCreatesBranchAndCommitsWithNativeGit(@TempDir Path directory) throws Exception {
        Path remote = createRemoteRepository(directory);
        RepoProperties repo = new RepoProperties();
        repo.setLocalBaseDir(directory.resolve("workspaces").toString());
        repo.setTargetRepoName("demo");
        repo.setCloneUrl(remote.toString());
        repo.setDefaultBranch("main");
        repo.setWorkingBranchPrefix("ai-devops");
        repo.setGitCommand("git");
        repo.setGitTimeoutSeconds(30);

        GithubProperties github = new GithubProperties();
        github.setUserName("ai-devops-test");
        github.setUserEmail("ai-devops-test@example.com");
        github.setTokenEnv("UNDEFINED_GITHUB_TOKEN_FOR_TEST");

        IncidentContext incident = new IncidentContext();
        incident.setIncidentId("INC-TEST-1");
        incident.setErrorFingerprint("rowkey-error");

        RepoManager manager = new RepoManager(repo, github);
        RepoWorkspace workspace = manager.prepare(incident, "task-1");
        Files.write(workspace.getDirectory().resolve("README.md"),
                "changed\n".getBytes(StandardCharsets.UTF_8));
        String commitId = manager.commit(workspace, incident, Arrays.asList("README.md"));

        assertTrue(commitId.matches("[0-9a-f]{40}"));
        assertEquals("ai-devops/INC-TEST-1/rowkey-error", workspace.getBranch());
        assertEquals(workspace.getBranch(),
                runGit(workspace.getDirectory(), "branch", "--show-current").trim());
    }

    private Path createRemoteRepository(Path directory) throws Exception {
        Path seed = Files.createDirectories(directory.resolve("seed"));
        Path remote = directory.resolve("remote.git");
        runGit(seed, "init");
        runGit(seed, "config", "user.name", "seed");
        runGit(seed, "config", "user.email", "seed@example.com");
        Files.write(seed.resolve("README.md"), "initial\n".getBytes(StandardCharsets.UTF_8));
        runGit(seed, "add", "README.md");
        runGit(seed, "commit", "-m", "initial");
        runGit(seed, "branch", "-M", "main");
        runGit(directory, "init", "--bare", remote.toString());
        runGit(seed, "remote", "add", "origin", remote.toString());
        runGit(seed, "push", "origin", "main");
        return remote;
    }

    private String runGit(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add("git");
        command.addAll(Arrays.asList(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        InputStream input = process.getInputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        int exitCode = process.waitFor();
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        if (exitCode != 0) {
            throw new IllegalStateException("git command failed: " + text);
        }
        return text;
    }
}
