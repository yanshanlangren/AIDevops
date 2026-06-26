package com.example.aidevops.patch;

import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.model.FileEdit;
import com.example.aidevops.model.LlmPlan;
import com.example.aidevops.model.NewFile;
import com.example.aidevops.model.PatchApplyCheckResult;
import com.example.aidevops.model.PatchValidationResult;
import com.example.aidevops.repo.RepoWorkspace;
import com.example.aidevops.security.SecretRedactor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchServiceTest {

    @Test
    void acceptsSmallSourceAndTestPatch() {
        PatchValidationResult result = service().preflight(
                "diff --git a/src/main/java/demo/A.java b/src/main/java/demo/A.java\n"
                        + "--- a/src/main/java/demo/A.java\n"
                        + "+++ b/src/main/java/demo/A.java\n"
                        + "@@ -1 +1 @@\n-old\n+new\n"
                        + "diff --git a/src/test/java/demo/ATest.java b/src/test/java/demo/ATest.java\n"
                        + "--- a/src/test/java/demo/ATest.java\n"
                        + "+++ b/src/test/java/demo/ATest.java\n"
                        + "@@ -1 +1 @@\n-old\n+new\n");
        assertTrue(result.isValid());
    }

    @Test
    void blocksProductionPathBeforePatchApplication() {
        PatchValidationResult result = service().preflight(
                "diff --git a/deploy/prod/app.yml b/deploy/prod/app.yml\n"
                        + "--- a/deploy/prod/app.yml\n"
                        + "+++ b/deploy/prod/app.yml\n"
                        + "@@ -1 +1 @@\n-old\n+new\n");
        assertFalse(result.isValid());
        assertTrue(result.getBlockedReasons().toString().contains("blacklisted"));
    }

    @Test
    void detectsCorruptPatchWithGitApplyCheck(@TempDir Path directory) throws Exception {
        Git git = Git.init().setDirectory(directory.toFile()).call();
        RepoWorkspace workspace = new RepoWorkspace(directory, git);
        String corruptPatch = "diff --git a/src/test/java/demo/ATest.java b/src/test/java/demo/ATest.java\n"
                + "new file mode 100644\n"
                + "--- /dev/null\n"
                + "+++ b/src/test/java/demo/ATest.java\n"
                + "@@ -0,0 +1,2 @@\n"
                + "+one\n";

        PatchValidationResult preflight = service().preflight(corruptPatch);
        PatchApplyCheckResult check = service().checkApply(workspace, corruptPatch);

        assertTrue(preflight.isValid());
        assertFalse(check.isValid());
        assertTrue(check.getMessage().contains("corrupt patch")
                || check.getMessage().contains("expected"));
    }

    @Test
    void appliesStructuredEditsAndGeneratesRealDiff(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("src/main/java/demo/A.java");
        Files.createDirectories(source.getParent());
        Files.write(source, "class A {\n    String value = \"old\";\n}\n".getBytes(StandardCharsets.UTF_8));
        Git git = Git.init().setDirectory(directory.toFile()).call();
        git.add().addFilepattern("src/main/java/demo/A.java").call();
        git.commit()
                .setMessage("initial")
                .setAuthor("test", "test@example.com")
                .setCommitter("test", "test@example.com")
                .call();
        RepoWorkspace workspace = new RepoWorkspace(directory, git);

        FileEdit edit = new FileEdit();
        edit.setPath("src/main/java/demo/A.java");
        edit.setOldText("String value = \"old\";");
        edit.setNewText("String value = \"new\";");
        NewFile test = new NewFile();
        test.setPath("src/test/java/demo/ATest.java");
        test.setContent("class ATest {\n}\n");
        LlmPlan plan = new LlmPlan();
        plan.setFileEdits(Collections.singletonList(edit));
        plan.setNewFiles(Collections.singletonList(test));

        PatchService service = service();
        PatchValidationResult preflight = service.preflightStructured(plan);
        service.applyStructuredEdits(workspace, plan);
        String diff = service.currentDiff(workspace);

        assertTrue(preflight.isValid());
        assertTrue(diff.contains("String value = \"new\";"));
        assertTrue(diff.contains("src/test/java/demo/ATest.java"));
    }

    @Test
    void appliesStructuredEditWhenTargetUsesCrlfAndModelUsesLf(@TempDir Path directory) throws Exception {
        Path source = directory.resolve("src/main/java/demo/A.java");
        Files.createDirectories(source.getParent());
        Files.write(source, ("class A {\r\n"
                + "    //获取rowkey域的值\r\n"
                + "    String value = rowkeyList.get(rowKeyField.getIndex());\r\n"
                + "}\r\n").getBytes(StandardCharsets.UTF_8));
        Git git = Git.init().setDirectory(directory.toFile()).call();
        git.add().addFilepattern("src/main/java/demo/A.java").call();
        git.commit()
                .setMessage("initial")
                .setAuthor("test", "test@example.com")
                .setCommitter("test", "test@example.com")
                .call();
        RepoWorkspace workspace = new RepoWorkspace(directory, git);

        FileEdit edit = new FileEdit();
        edit.setPath("src/main/java/demo/A.java");
        edit.setOldText("    //获取rowkey域的值\n"
                + "    String value = rowkeyList.get(rowKeyField.getIndex());");
        edit.setNewText("    //获取rowkey域的值\n"
                + "    if (rowKeyField.getIndex() >= rowkeyList.size()) {\n"
                + "        throw new IllegalArgumentException(\"missing segment\");\n"
                + "    }\n"
                + "    String value = rowkeyList.get(rowKeyField.getIndex());");
        LlmPlan plan = new LlmPlan();
        plan.setFileEdits(Collections.singletonList(edit));

        service().applyStructuredEdits(workspace, plan);

        String updated = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        assertTrue(updated.contains("if (rowKeyField.getIndex() >= rowkeyList.size())"));
        assertTrue(updated.contains("}\r\n"));
        assertFalse(updated.contains("}\n    String value"));
    }

    private PatchService service() {
        PolicyProperties policy = new PolicyProperties();
        policy.setPathWhitelist(Arrays.asList("src/main/java/", "src/test/java/", "pom.xml"));
        policy.setPathBlacklist(Arrays.asList("deploy/prod/", "config/prod/"));
        policy.setMaxChangedFiles(8);
        policy.setMaxDiffLines(500);
        policy.setRequireTests(true);
        policy.setTestPathPrefix("src/test/java/");
        policy.setBlockSecretsInDiff(true);
        policy.setBlockProductionConfigEdit(true);
        return new PatchService(policy, new SecretRedactor());
    }
}
