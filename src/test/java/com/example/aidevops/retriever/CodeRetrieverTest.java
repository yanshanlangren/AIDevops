package com.example.aidevops.retriever;

import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.RecentCommit;
import com.example.aidevops.model.RetrievalResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeRetrieverTest {
    @TempDir
    Path repository;

    @Test
    void retrievesSourceFromMavenModuleDirectory() throws Exception {
        Path source = repository.resolve(
                "hms/src/main/java/com/abchina/hdqs/hms/hao/HBaseHao.java");
        Files.createDirectories(source.getParent());
        Files.write(source,
                "class HBaseHao { void genRowKey() {} }".getBytes(StandardCharsets.UTF_8));

        IncidentContext incident = new IncidentContext();
        incident.setSuspectedComponents(Arrays.asList("HBaseHao", "genRowKey"));
        RecentCommit commit = new RecentCommit();
        commit.setFiles(Arrays.asList(
                "hms/src/main/java/com/abchina/hdqs/hms/hao/HBaseHao.java"));
        incident.setRecentCommits(Arrays.asList(commit));

        ModelProperties model = new ModelProperties();
        PolicyProperties policy = new PolicyProperties();
        policy.setPathBlacklist(Arrays.asList("deploy/prod/"));

        RetrievalResult result = new CodeRetriever(model, policy).retrieve(repository, incident);

        assertEquals(1, result.getTargetFiles().size());
        assertEquals("hms/src/main/java/com/abchina/hdqs/hms/hao/HBaseHao.java",
                result.getTargetFiles().get(0).getPath());
    }

    @Test
    void sendsOnlySnippetAroundMatchedMethod() throws Exception {
        Path source = repository.resolve("hms/src/main/java/demo/LargeService.java");
        Files.createDirectories(source.getParent());
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            content.append(i == 120 ? "void genRowKey() {}\n" : "String line" + i + ";\n");
        }
        Files.write(source, content.toString().getBytes(StandardCharsets.UTF_8));

        IncidentContext incident = new IncidentContext();
        incident.setSuspectedComponents(Arrays.asList("genRowKey"));
        ModelProperties model = new ModelProperties();
        model.setSnippetContextLines(2);
        model.setMaxSnippetsPerFile(1);
        model.setMaxFileChars(10000);
        PolicyProperties policy = new PolicyProperties();
        policy.setPathBlacklist(Arrays.asList("deploy/prod/"));

        RetrievalResult result = new CodeRetriever(model, policy).retrieve(repository, incident);
        String snippet = result.getTargetFiles().get(0).getContent();

        assertTrue(snippet.contains("genRowKey"));
        assertTrue(snippet.contains("line118"));
        assertFalse(snippet.contains("line1;"));
        assertFalse(snippet.contains("line200"));
    }
}
