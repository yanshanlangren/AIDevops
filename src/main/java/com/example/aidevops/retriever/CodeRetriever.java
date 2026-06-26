package com.example.aidevops.retriever;

import com.example.aidevops.config.ModelProperties;
import com.example.aidevops.config.PolicyProperties;
import com.example.aidevops.model.CandidateFile;
import com.example.aidevops.model.IncidentContext;
import com.example.aidevops.model.RecentCommit;
import com.example.aidevops.model.RetrievalResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class CodeRetriever {
    private final ModelProperties model;
    private final PolicyProperties policy;

    public CodeRetriever(ModelProperties model, PolicyProperties policy) {
        this.model = model;
        this.policy = policy;
    }

    public RetrievalResult retrieve(Path repository, IncidentContext incident) {
        final Set<String> exactPaths = recentPaths(incident);
        final Set<String> keywords = keywords(incident);
        final List<CandidateFile> candidates = new ArrayList<CandidateFile>();
        final List<CandidateFile> ignored = new ArrayList<CandidateFile>();

        try (Stream<Path> paths = Files.walk(repository)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isCodeFile(repository.relativize(path).toString()))
                    .forEach(path -> score(repository, path, exactPaths, keywords, candidates, ignored));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan repository " + repository, e);
        }

        Collections.sort(candidates, new Comparator<CandidateFile>() {
            @Override
            public int compare(CandidateFile left, CandidateFile right) {
                return Integer.compare(right.getScore(), left.getScore());
            }
        });
        List<CandidateFile> selected = candidates.size() > model.getMaxFiles()
                ? new ArrayList<CandidateFile>(candidates.subList(0, model.getMaxFiles()))
                : candidates;

        RetrievalResult result = new RetrievalResult();
        result.setTargetFiles(selected);
        result.setIgnoredFiles(ignored);
        return result;
    }

    private void score(Path repository, Path file, Set<String> exactPaths, Set<String> keywords,
                       List<CandidateFile> candidates, List<CandidateFile> ignored) {
        String relative = normalize(repository.relativize(file).toString());
        if (isBlacklisted(relative)) {
            ignored.add(new CandidateFile(relative, "path blacklisted", 0, null));
            return;
        }
        int score = 0;
        List<String> reasons = new ArrayList<String>();
        if (exactPaths.contains(relative)) {
            score += 100;
            reasons.add("matched recent commit");
        }
        String lowerPath = relative.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (lowerPath.contains(keyword)) {
                score += 20;
                reasons.add("matched " + keyword);
            }
        }
        String content = read(file);
        String normalizedContent = normalizeSearch(content);
        for (String keyword : keywords) {
            if (normalizedContent.contains(keyword)) {
                score += 5;
            }
        }
        if (isTestSource(relative) && score > 0) {
            score += 5;
            reasons.add("related test source");
        }
        if (score > 0) {
            candidates.add(new CandidateFile(
                    relative,
                    join(reasons),
                    score,
                    extractRelevantSnippets(content, keywords, exactPaths.contains(relative))));
        }
    }

    private Set<String> recentPaths(IncidentContext incident) {
        Set<String> paths = new HashSet<String>();
        if (incident.getRecentCommits() != null) {
            for (RecentCommit commit : incident.getRecentCommits()) {
                if (commit.getFiles() != null) {
                    for (String file : commit.getFiles()) {
                        paths.add(normalize(file));
                    }
                }
            }
        }
        return paths;
    }

    private Set<String> keywords(IncidentContext incident) {
        Set<String> values = new LinkedHashSet<String>();
        if (incident.getSuspectedComponents() != null) {
            for (String value : incident.getSuspectedComponents()) {
                addKeyword(values, value);
            }
        }
        addJsonKeyword(values, incident.getLogSummary(), "class");
        addJsonKeyword(values, incident.getLogSummary(), "method");
        addKeyword(values, incident.getProblemType());
        addKeyword(values, incident.getErrorFingerprint());
        values.add("rowkey");
        values.add("query");
        values.add("condition");
        return values;
    }

    private void addJsonKeyword(Set<String> values, JsonNode node, String field) {
        if (node != null && node.hasNonNull(field)) {
            addKeyword(values, node.get(field).asText());
        }
    }

    private void addKeyword(Set<String> values, String value) {
        if (value == null) {
            return;
        }
        String normalized = normalizeSearch(value).replace("service", "");
        if (normalized.length() >= 3) {
            values.add(normalized);
        }
    }

    private boolean isCodeFile(String path) {
        String normalized = normalize(path);
        return ((normalized.startsWith("src/") || normalized.contains("/src/"))
                && (normalized.endsWith(".java")
                || normalized.endsWith(".xml")
                || normalized.endsWith(".yml")
                || normalized.endsWith(".yaml")
                || normalized.endsWith(".properties")))
                || normalized.equals("pom.xml")
                || normalized.endsWith("/pom.xml");
    }

    private boolean isTestSource(String path) {
        String normalized = normalize(path);
        return normalized.startsWith("src/test/") || normalized.contains("/src/test/");
    }

    private boolean isBlacklisted(String path) {
        for (String blacklisted : policy.getPathBlacklist()) {
            if (path.startsWith(normalize(blacklisted))) {
                return true;
            }
        }
        return false;
    }

    private String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read source file " + file, e);
        }
    }

    private String truncate(String value) {
        if (value.length() <= model.getMaxFileChars()) {
            return value;
        }
        return value.substring(0, model.getMaxFileChars()) + "\n/* truncated */";
    }

    private String extractRelevantSnippets(String content, Set<String> keywords, boolean exactPath) {
        String[] lines = content.split("\\r?\\n", -1);
        int maxSnippets = Math.max(1, model.getMaxSnippetsPerFile());
        Set<Integer> selectedHits = new LinkedHashSet<Integer>();
        for (String keyword : keywords) {
            for (int i = 0; i < lines.length; i++) {
                String normalizedLine = normalizeSearch(lines[i]);
                if (normalizedLine.contains(keyword)) {
                    selectedHits.add(i);
                    break;
                }
            }
            if (selectedHits.size() >= maxSnippets) {
                break;
            }
        }
        List<Integer> hits = new ArrayList<Integer>(selectedHits);
        Collections.sort(hits);

        List<int[]> windows = new ArrayList<int[]>();
        int context = Math.max(0, model.getSnippetContextLines());
        for (Integer hit : hits) {
            int start = Math.max(0, hit - context);
            int end = Math.min(lines.length - 1, hit + context);
            if (!windows.isEmpty() && start <= windows.get(windows.size() - 1)[1] + 1) {
                windows.get(windows.size() - 1)[1] = Math.max(windows.get(windows.size() - 1)[1], end);
            } else if (windows.size() < maxSnippets) {
                windows.add(new int[]{start, end});
            }
            if (windows.size() >= maxSnippets
                    && hit > windows.get(windows.size() - 1)[1]) {
                break;
            }
        }
        if (windows.isEmpty() && exactPath) {
            windows.add(new int[]{0, Math.min(lines.length - 1, context * 2)});
        }

        StringBuilder snippet = new StringBuilder();
        for (int[] window : windows) {
            if (snippet.length() > 0) {
                snippet.append("\n// ... unrelated code omitted ...\n");
            }
            snippet.append("// source lines ")
                    .append(window[0] + 1)
                    .append("-")
                    .append(window[1] + 1)
                    .append("\n");
            for (int line = window[0]; line <= window[1]; line++) {
                snippet.append(String.format(Locale.ROOT, "%5d | %s%n", line + 1, lines[line]));
                if (snippet.length() >= model.getMaxFileChars()) {
                    return truncate(snippet.toString());
                }
            }
        }
        if (snippet.length() == 0) {
            return truncate(content);
        }
        return truncate(snippet.toString());
    }

    private String normalizeSearch(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replaceAll("\\s+", "");
    }

    private String normalize(String value) {
        return value.replace('\\', '/');
    }

    private String join(List<String> values) {
        if (values.isEmpty()) {
            return "content keyword match";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
