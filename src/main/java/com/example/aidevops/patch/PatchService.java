package com.example.aidevops.patch;

import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.model.FileEdit;
import com.example.aidevops.model.LlmPlan;
import com.example.aidevops.model.NewFile;
import com.example.aidevops.model.PatchApplyCheckResult;
import com.example.aidevops.model.PatchValidationResult;
import com.example.aidevops.repo.RepoWorkspace;
import com.example.aidevops.security.SecretRedactor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
        PatchApplyCheckResult result = runGitApply(workspace, unifiedDiff, false);
        if (!result.isValid()) {
            throw new IllegalStateException("Generated unified diff cannot be applied cleanly: "
                    + result.getMessage());
        }
    }

    public PatchApplyCheckResult checkApply(RepoWorkspace workspace, String unifiedDiff) {
        if (!StringUtils.hasText(unifiedDiff)) {
            PatchApplyCheckResult result = new PatchApplyCheckResult();
            result.setValid(false);
            result.setCommand("git apply --check --whitespace=nowarn -");
            result.setMessage("Model output contains no unified_diff");
            return result;
        }
        return runGitApply(workspace, unifiedDiff, true);
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
        validateDeclaredFiles(declaredFiles, result, "patch does not declare a test file change");
        if (policy.isBlockSecretsInDiff() && redactor.containsSecret(unifiedDiff)) {
            result.getBlockedReasons().add("possible secret detected in diff");
        }
        detectAntiPatterns(unifiedDiff, result);
        result.setValid(result.getBlockedReasons().isEmpty());
        return result;
    }

    public PatchValidationResult preflightStructured(LlmPlan plan) {
        PatchValidationResult result = new PatchValidationResult();
        if (plan == null || !plan.hasStructuredEdits()) {
            result.getBlockedReasons().add("model output contains no file_edits or new_files");
            return result;
        }
        Set<String> declaredFiles = new LinkedHashSet<String>();
        int diffLines = 0;
        StringBuilder structuredText = new StringBuilder();
        if (plan.getFileEdits() != null) {
            for (FileEdit edit : plan.getFileEdits()) {
                String path = normalize(edit == null || edit.getPath() == null ? "" : edit.getPath());
                declaredFiles.add(path);
                if (edit != null) {
                    diffLines += countLines(edit.getOldText()) + countLines(edit.getNewText());
                    structuredText.append(edit.getOldText()).append('\n')
                            .append(edit.getNewText()).append('\n');
                }
            }
        }
        if (plan.getNewFiles() != null) {
            for (NewFile file : plan.getNewFiles()) {
                String path = normalize(file == null || file.getPath() == null ? "" : file.getPath());
                declaredFiles.add(path);
                if (file != null) {
                    diffLines += countLines(file.getContent());
                    structuredText.append(file.getContent()).append('\n');
                }
            }
        }
        result.setChangedFiles(new ArrayList<String>(declaredFiles));
        result.setDiffLines(diffLines);
        if (declaredFiles.isEmpty()) {
            result.getBlockedReasons().add("structured edit contains no target files");
        }
        if (declaredFiles.size() > policy.getMaxChangedFiles()) {
            result.getBlockedReasons().add("declared file count exceeds " + policy.getMaxChangedFiles());
        }
        if (result.getDiffLines() > policy.getMaxDiffLines()) {
            result.getBlockedReasons().add("structured edit line count exceeds " + policy.getMaxDiffLines());
        }
        validateDeclaredFiles(declaredFiles, result, "structured edit does not declare a test file change");
        if (policy.isBlockSecretsInDiff() && redactor.containsSecret(structuredText.toString())) {
            result.getBlockedReasons().add("possible secret detected in structured edit");
        }
        result.setValid(result.getBlockedReasons().isEmpty());
        return result;
    }

    public void applyStructuredEdits(RepoWorkspace workspace, LlmPlan plan) {
        if (plan == null || !plan.hasStructuredEdits()) {
            throw new IllegalArgumentException("Model output contains no structured edits");
        }
        try {
            if (plan.getFileEdits() != null) {
                for (FileEdit edit : plan.getFileEdits()) {
                    applyFileEdit(workspace, edit);
                }
            }
            if (plan.getNewFiles() != null) {
                for (NewFile file : plan.getNewFiles()) {
                    createNewFile(workspace, file);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot apply structured edit", e);
        }
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

        Set<String> normalizedChanged = new LinkedHashSet<String>();
        for (String file : changed) {
            normalizedChanged.add(normalize(file));
        }
        validateDeclaredFiles(normalizedChanged, result, "patch does not add or modify a test file");
        if (policy.isBlockSecretsInDiff() && redactor.containsSecret(unifiedDiff)) {
            result.getBlockedReasons().add("possible secret detected in diff");
        }
        detectAntiPatterns(unifiedDiff, result);
        result.setValid(result.getBlockedReasons().isEmpty());
        return result;
    }

    public String currentDiff(RepoWorkspace workspace) {
        StringBuilder diff = new StringBuilder();
        PatchApplyCheckResult tracked = runGitCommand(
                workspace, Arrays.asList(workspace.getGitCommand(), "diff", "--no-ext-diff", "--binary"));
        if (!tracked.isValid()) {
            throw new IllegalStateException("Cannot read current repository diff: " + tracked.getMessage());
        }
        diff.append(tracked.getStdout() == null ? "" : tracked.getStdout());
        for (String file : untrackedFiles(workspace)) {
            String normalized = normalize(file);
            Path target = workspace.getDirectory().resolve(normalized);
            if (!Files.exists(target)) {
                continue;
            }
            PatchApplyCheckResult untracked = runGitCommand(
                    workspace, Arrays.asList(workspace.getGitCommand(),
                            "diff", "--no-index", "--", "/dev/null", normalized));
            if (untracked.getExitCode() != null && untracked.getExitCode().intValue() == 1) {
                diff.append(untracked.getStdout() == null ? "" : untracked.getStdout());
            }
        }
        return diff.toString();
    }

    private Set<String> statusFiles(RepoWorkspace workspace) {
        return parseStatus(workspace, false);
    }

    private Set<String> untrackedFiles(RepoWorkspace workspace) {
        return parseStatus(workspace, true);
    }

    private Set<String> parseStatus(RepoWorkspace workspace, boolean untrackedOnly) {
        PatchApplyCheckResult status = runGitCommand(workspace, Arrays.asList(
                workspace.getGitCommand(), "status", "--porcelain=v1", "-z", "--untracked-files=all"));
        if (!status.isValid()) {
            throw new IllegalStateException("Cannot inspect changed files: " + status.getMessage());
        }
        Set<String> files = new LinkedHashSet<String>();
        String output = status.getStdout() == null ? "" : status.getStdout();
        String[] records = output.split("\\u0000", -1);
        for (int index = 0; index < records.length; index++) {
            String record = records[index];
            if (record.length() < 4 || record.charAt(2) != ' ') {
                continue;
            }
            String code = record.substring(0, 2);
            String path = normalize(record.substring(3));
            if (!untrackedOnly || "??".equals(code)) {
                files.add(path);
            }
            if (isRenameOrCopy(code) && index + 1 < records.length) {
                index++;
            }
        }
        return files;
    }

    private boolean isRenameOrCopy(String code) {
        return code.indexOf('R') >= 0 || code.indexOf('C') >= 0;
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

    private void validateDeclaredFiles(Set<String> files, PatchValidationResult result, String missingTestMessage) {
        boolean testChanged = false;
        for (String file : files) {
            if (file.startsWith("/") || file.contains("../") || file.equals("..") || file.length() == 0) {
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
            result.getBlockedReasons().add(missingTestMessage);
        }
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
        if (diff == null) {
            return 0;
        }
        int count = 0;
        for (String line : diff.split("\\r?\\n")) {
            if ((line.startsWith("+") && !line.startsWith("+++"))
                    || (line.startsWith("-") && !line.startsWith("---"))) {
                count++;
            }
        }
        return count;
    }

    private int countLines(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.split("\\r?\\n", -1).length;
    }

    private void applyFileEdit(RepoWorkspace workspace, FileEdit edit) throws IOException {
        if (edit == null || !StringUtils.hasText(edit.getPath())) {
            throw new IllegalArgumentException("structured file_edit misses path");
        }
        if (edit.getOldText() == null || edit.getNewText() == null) {
            throw new IllegalArgumentException("structured file_edit misses old_text or new_text for "
                    + edit.getPath());
        }
        Path target = safeResolve(workspace, edit.getPath());
        if (!Files.isRegularFile(target)) {
            throw new IllegalStateException("structured edit target file does not exist: " + edit.getPath());
        }
        String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        String lineSeparator = detectLineSeparator(content);
        String oldText = edit.getOldText();
        String newText = convertLineEndings(edit.getNewText(), lineSeparator);
        int first = content.indexOf(oldText);
        if (first < 0) {
            oldText = convertLineEndings(edit.getOldText(), lineSeparator);
            first = content.indexOf(oldText);
        }
        if (first < 0) {
            throw new IllegalStateException("structured edit old_text was not found in " + edit.getPath());
        }
        int second = content.indexOf(oldText, first + oldText.length());
        if (second >= 0) {
            throw new IllegalStateException("structured edit old_text matched multiple locations in "
                    + edit.getPath());
        }
        String updated = content.substring(0, first) + newText
                + content.substring(first + oldText.length());
        Files.write(target, updated.getBytes(StandardCharsets.UTF_8));
    }

    private String detectLineSeparator(String content) {
        if (content != null && content.contains("\r\n")) {
            return "\r\n";
        }
        if (content != null && content.contains("\r")) {
            return "\r";
        }
        return "\n";
    }

    private String convertLineEndings(String value, String lineSeparator) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        if ("\n".equals(lineSeparator)) {
            return normalized;
        }
        return normalized.replace("\n", lineSeparator);
    }

    private void createNewFile(RepoWorkspace workspace, NewFile file) throws IOException {
        if (file == null || !StringUtils.hasText(file.getPath())) {
            throw new IllegalArgumentException("structured new_file misses path");
        }
        if (file.getContent() == null) {
            throw new IllegalArgumentException("structured new_file misses content for " + file.getPath());
        }
        Path target = safeResolve(workspace, file.getPath());
        if (Files.exists(target)) {
            throw new IllegalStateException("structured new_file already exists: " + file.getPath());
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, file.getContent().getBytes(StandardCharsets.UTF_8));
    }

    private Path safeResolve(RepoWorkspace workspace, String path) {
        String normalized = normalize(path);
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")) {
            throw new IllegalArgumentException("unsafe structured edit path: " + path);
        }
        Path root = workspace.getDirectory().toAbsolutePath().normalize();
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("structured edit path escapes repository: " + path);
        }
        return target;
    }

    private PatchApplyCheckResult runGitApply(RepoWorkspace workspace, String unifiedDiff, boolean checkOnly) {
        List<String> command = new ArrayList<String>();
        command.add(workspace.getGitCommand());
        command.add("apply");
        if (checkOnly) {
            command.add("--check");
        }
        command.add("--whitespace=nowarn");
        command.add("-");
        PatchApplyCheckResult result = new PatchApplyCheckResult();
        result.setCommand(joinCommand(command));
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workspace.getDirectory().toFile());
            process = builder.start();
            StreamCollector stdout = new StreamCollector(process.getInputStream());
            StreamCollector stderr = new StreamCollector(process.getErrorStream());
            Thread stdoutThread = new Thread(stdout, "git-apply-stdout");
            Thread stderrThread = new Thread(stderr, "git-apply-stderr");
            stdoutThread.start();
            stderrThread.start();
            Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(unifiedDiff);
            writer.close();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.setValid(false);
                result.setMessage("git apply timed out after 30 seconds");
                return result;
            }
            stdoutThread.join(1000L);
            stderrThread.join(1000L);
            result.setExitCode(process.exitValue());
            result.setStdout(stdout.getContent());
            result.setStderr(stderr.getContent());
            result.setValid(process.exitValue() == 0);
            result.setMessage(buildGitApplyMessage(result));
            return result;
        } catch (IOException e) {
            result.setValid(false);
            result.setMessage("cannot execute git apply: " + e.getMessage());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            result.setValid(false);
            result.setMessage("interrupted while executing git apply");
            return result;
        }
    }

    private PatchApplyCheckResult runGitCommand(RepoWorkspace workspace, List<String> command) {
        PatchApplyCheckResult result = new PatchApplyCheckResult();
        result.setCommand(joinCommand(command));
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workspace.getDirectory().toFile());
            process = builder.start();
            StreamCollector stdout = new StreamCollector(process.getInputStream());
            StreamCollector stderr = new StreamCollector(process.getErrorStream());
            Thread stdoutThread = new Thread(stdout, "git-command-stdout");
            Thread stderrThread = new Thread(stderr, "git-command-stderr");
            stdoutThread.start();
            stderrThread.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.setValid(false);
                result.setMessage("git command timed out after 30 seconds");
                return result;
            }
            stdoutThread.join(1000L);
            stderrThread.join(1000L);
            result.setExitCode(process.exitValue());
            result.setStdout(stdout.getContent());
            result.setStderr(stderr.getContent());
            result.setValid(process.exitValue() == 0);
            result.setMessage(buildGitApplyMessage(result));
            return result;
        } catch (IOException e) {
            result.setValid(false);
            result.setMessage("cannot execute git command: " + e.getMessage());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            result.setValid(false);
            result.setMessage("interrupted while executing git command");
            return result;
        }
    }

    private String buildGitApplyMessage(PatchApplyCheckResult result) {
        if (result.isValid()) {
            return "git apply check passed";
        }
        if (StringUtils.hasText(result.getStderr())) {
            return result.getStderr().trim();
        }
        if (StringUtils.hasText(result.getStdout())) {
            return result.getStdout().trim();
        }
        return "git apply failed with exit code " + result.getExitCode();
    }

    private String joinCommand(List<String> command) {
        StringBuilder builder = new StringBuilder();
        for (String part : command) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private void detectAntiPatterns(String diff, PatchValidationResult result) {
        String compact = diff == null ? "" : diff.replaceAll("\\s+", " ");
        if (compact.contains("catch (") && compact.matches(".*catch \\([^)]*\\) \\{\\s*\\}.*")) {
            result.getBlockedReasons().add("empty catch block detected");
        }
        if (compact.contains("return true;") && compact.contains("TODO")) {
            result.getBlockedReasons().add("possible hard-coded success path detected");
        }
        if (policy.isBlockProductionConfigEdit()
                && (compact.contains("application-prod") || compact.contains("deploy/prod"))) {
            result.getBlockedReasons().add("production configuration change detected");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/');
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
                // Process-level result still reports the useful failure.
            }
        }

        private String getContent() {
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
