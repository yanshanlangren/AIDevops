package com.example.aidevops.patch;

import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.model.PatchValidationResult;
import com.example.aidevops.repo.RepoWorkspace;
import com.example.aidevops.security.SecretRedactor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatchService {
    private final PolicyProperties policy;
    private final SecretRedactor redactor;

    public PatchService(PolicyProperties policy, SecretRedactor redactor) {
        this.policy = policy;
        this.redactor = redactor;
    }

    public void apply(RepoWorkspace workspace, String unifiedDiff) {
        if (!StringUtils.hasText(unifiedDiff)) {
            throw new IllegalArgumentException("Model output contains no unified_diff");
        }
        try {
            workspace.getGit().apply()
                    .setPatch(new ByteArrayInputStream(unifiedDiff.getBytes(StandardCharsets.UTF_8)))
                    .call();
        } catch (GitAPIException e) {
            throw new IllegalStateException("Generated unified diff cannot be applied cleanly", e);
        }
    }

    public PatchValidationResult preflight(String unifiedDiff) {
        PatchValidationResult result = new PatchValidationResult();
        if (!StringUtils.hasText(unifiedDiff)) {
            result.getBlockedReasons().add("model output contains no unified diff");
            return result;
        }
        Set<String> declaredFiles = declaredFiles(unifiedDiff);
        result.setChangedFiles(new ArrayList<String>(declaredFiles));
        result.setDiffLines(countDiffLines(unifiedDiff));
        if (declaredFiles.isEmpty()) {
            result.getBlockedReasons().add("unified diff contains no target files");
        }
        if (declaredFiles.size() > policy.getMaxChangedFiles()) {
            result.getBlockedReasons().add("declared file count exceeds " + policy.getMaxChangedFiles());
        }
        if (result.getDiffLines() > policy.getMaxDiffLines()) {
            result.getBlockedReasons().add("diff line count exceeds " + policy.getMaxDiffLines());
        }
        boolean testChanged = false;
        for (String file : declaredFiles) {
            if (file.startsWith("/") || file.contains("../") || file.equals("..")) {
                result.getBlockedReasons().add("unsafe patch path: " + file);
                continue;
            }
            if (!isWhitelisted(file)) {
                result.getBlockedReasons().add("path is outside allowlist: " + file);
            }
            if (isBlacklisted(file)) {
                result.getBlockedReasons().add("path is blacklisted: " + file);
            }
            if (file.startsWith(policy.getTestPathPrefix())) {
                testChanged = true;
            }
        }
        if (policy.isRequireTests() && !testChanged) {
            result.getBlockedReasons().add("patch does not declare a test file change");
        }
        if (policy.isBlockSecretsInDiff() && redactor.containsSecret(unifiedDiff)) {
            result.getBlockedReasons().add("possible secret detected in diff");
        }
        detectAntiPatterns(unifiedDiff, result);
        result.setValid(result.getBlockedReasons().isEmpty());
        return result;
    }

    public PatchValidationResult validate(RepoWorkspace workspace, String unifiedDiff) {
        PatchValidationResult result = new PatchValidationResult();
        Set<String> changed = statusFiles(workspace);
        result.setChangedFiles(new ArrayList<String>(changed));
        result.setDiffLines(countDiffLines(unifiedDiff));

        if (changed.isEmpty()) {
            result.getBlockedReasons().add("patch produced no changed files");
        }
        if (changed.size() > policy.getMaxChangedFiles()) {
            result.getBlockedReasons().add("changed file count exceeds " + policy.getMaxChangedFiles());
        }
        if (result.getDiffLines() > policy.getMaxDiffLines()) {
            result.getBlockedReasons().add("diff line count exceeds " + policy.getMaxDiffLines());
        }

        boolean testChanged = false;
        for (String file : changed) {
            String normalized = normalize(file);
            if (!isWhitelisted(normalized)) {
                result.getBlockedReasons().add("path is outside allowlist: " + normalized);
            }
            if (isBlacklisted(normalized)) {
                result.getBlockedReasons().add("path is blacklisted: " + normalized);
            }
            if (normalized.startsWith(policy.getTestPathPrefix())) {
                testChanged = true;
            }
        }
        if (policy.isRequireTests() && !testChanged) {
            result.getBlockedReasons().add("patch does not add or modify a test file");
        }
        if (policy.isBlockSecretsInDiff() && redactor.containsSecret(unifiedDiff)) {
            result.getBlockedReasons().add("possible secret detected in diff");
        }
        detectAntiPatterns(unifiedDiff, result);
        result.setValid(result.getBlockedReasons().isEmpty());
        return result;
    }

    public String currentDiff(RepoWorkspace workspace) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workspace.getGit().diff().setOutputStream(output).call();
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (GitAPIException e) {
            throw new IllegalStateException("Cannot read current repository diff", e);
        }
    }

    private Set<String> statusFiles(RepoWorkspace workspace) {
        try {
            Status status = workspace.getGit().status().call();
            Set<String> files = new LinkedHashSet<String>();
            files.addAll(status.getAdded());
            files.addAll(status.getChanged());
            files.addAll(status.getModified());
            files.addAll(status.getRemoved());
            files.addAll(status.getMissing());
            files.addAll(status.getUntracked());
            return files;
        } catch (GitAPIException e) {
            throw new IllegalStateException("Cannot inspect changed files", e);
        }
    }

    private Set<String> declaredFiles(String diff) {
        Set<String> files = new LinkedHashSet<String>();
        for (String line : diff.split("\\r?\\n")) {
            if (!line.startsWith("+++ ")) {
                continue;
            }
            String path = line.substring(4).trim();
            int tab = path.indexOf('\t');
            if (tab >= 0) {
                path = path.substring(0, tab);
            }
            if ("/dev/null".equals(path)) {
                continue;
            }
            if (path.startsWith("b/")) {
                path = path.substring(2);
            }
            files.add(normalize(path));
        }
        return files;
    }

    private boolean isWhitelisted(String file) {
        for (String allowed : policy.getPathWhitelist()) {
            String normalized = normalize(allowed);
            if (normalized.endsWith("/") && file.startsWith(normalized)) {
                return true;
            }
            if (file.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlacklisted(String file) {
        for (String denied : policy.getPathBlacklist()) {
            String normalized = normalize(denied);
            if (file.equals(normalized) || file.startsWith(normalized)) {
                return true;
            }
        }
        return false;
    }

    private int countDiffLines(String diff) {
        int count = 0;
        for (String line : diff.split("\\r?\\n")) {
            if ((line.startsWith("+") && !line.startsWith("+++"))
                    || (line.startsWith("-") && !line.startsWith("---"))) {
                count++;
            }
        }
        return count;
    }

    private void detectAntiPatterns(String diff, PatchValidationResult result) {
        String compact = diff.replaceAll("\\s+", " ");
        if (compact.contains("catch (") && compact.matches(".*catch \\([^)]*\\) \\{\\s*\\}.*")) {
            result.getBlockedReasons().add("empty catch block detected");
        }
        if (compact.contains("return true;") && compact.contains("TODO")) {
            result.getBlockedReasons().add("possible hard-coded success path detected");
        }
        if (policy.isBlockProductionConfigEdit()
                && (diff.contains("application-prod") || diff.contains("deploy/prod"))) {
            result.getBlockedReasons().add("production configuration change detected");
        }
    }

    private String normalize(String value) {
        return value.replace('\\', '/');
    }
}
