package com.example.aidevops.github;

import com.example.aidevops.config.GithubProperties;
import com.example.aidevops.model.PrResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Service
public class GithubPullRequestClient {
    private final GithubProperties github;

    public GithubPullRequestClient(GithubProperties github) {
        this.github = github;
    }

    public PrResult create(String branch, String title, String body) {
        String token = System.getenv(github.getTokenEnv());
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Missing GitHub token environment variable: " + github.getTokenEnv());
        }
        String url = strip(github.getApiBaseUrl()) + "/repos/" + github.getOwner() + "/" + github.getRepo() + "/pulls";
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("title", title);
        request.put("head", branch);
        request.put("base", github.getTargetBranch());
        request.put("body", body);
        request.put("draft", github.isDraft());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        RestTemplate rest = new RestTemplate();
        ResponseEntity<JsonNode> response = rest.exchange(
                url, HttpMethod.POST, new HttpEntity<Map<String, Object>>(request, headers), JsonNode.class);
        JsonNode json = response.getBody();
        PrResult result = new PrResult();
        result.setCreated(true);
        result.setBranch(branch);
        result.setUrl(json == null ? null : json.path("html_url").asText());
        result.setNumber(json != null && json.has("number") ? json.path("number").asInt() : null);
        List<String> warnings = new ArrayList<String>();
        if (result.getNumber() != null) {
            applyLabels(rest, headers, result.getNumber(), warnings);
            requestReviewers(rest, headers, result.getNumber(), warnings);
        }
        result.setMessage(warnings.isEmpty()
                ? "Pull request created"
                : "Pull request created; metadata warnings: " + warnings);
        return result;
    }

    private void applyLabels(RestTemplate rest, HttpHeaders headers, int number, List<String> warnings) {
        if (github.getLabels() == null || github.getLabels().isEmpty()) {
            return;
        }
        String url = strip(github.getApiBaseUrl()) + "/repos/" + github.getOwner() + "/"
                + github.getRepo() + "/issues/" + number + "/labels";
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("labels", github.getLabels());
        try {
            rest.exchange(url, HttpMethod.POST,
                    new HttpEntity<Map<String, Object>>(payload, headers), JsonNode.class);
        } catch (RuntimeException e) {
            warnings.add("labels were not applied");
        }
    }

    private void requestReviewers(RestTemplate rest, HttpHeaders headers, int number, List<String> warnings) {
        if (github.getReviewers() == null || github.getReviewers().isEmpty()) {
            return;
        }
        String url = strip(github.getApiBaseUrl()) + "/repos/" + github.getOwner() + "/"
                + github.getRepo() + "/pulls/" + number + "/requested_reviewers";
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("reviewers", github.getReviewers());
        try {
            rest.exchange(url, HttpMethod.POST,
                    new HttpEntity<Map<String, Object>>(payload, headers), JsonNode.class);
        } catch (RuntimeException e) {
            warnings.add("reviewers were not requested");
        }
    }

    private String strip(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
