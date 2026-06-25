package com.example.aidevops.patch;

import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.model.PatchValidationResult;
import com.example.aidevops.security.SecretRedactor;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

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
