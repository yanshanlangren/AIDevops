package com.example.aidevops.github;

import com.example.aidevops.config.GithubProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GithubAuthenticationTest {

    @Test
    void configuresUsernamePasswordForGitAndApi() {
        GithubProperties properties = new GithubProperties();
        properties.setAuthMode("username-password");
        properties.setAuthUsername("git-user");
        properties.setPasswordEnv("GIT_PASSWORD");
        GithubAuthentication authentication = new GithubAuthentication(
                properties, name -> "git-password");

        ProcessBuilder builder = new ProcessBuilder("git", "version");
        authentication.configureGit(builder, true);
        HttpHeaders headers = new HttpHeaders();
        authentication.configureApi(headers);

        assertEquals("git-user:git-password", decodeBasic(
                builder.environment().get("GIT_CONFIG_VALUE_0")));
        assertEquals("git-user:git-password", decodeBasic(
                headers.getFirst(HttpHeaders.AUTHORIZATION)));
    }

    @Test
    void keepsTokenModeForCompatibility() {
        GithubProperties properties = new GithubProperties();
        properties.setAuthMode("token");
        properties.setTokenEnv("GITHUB_TOKEN");
        GithubAuthentication authentication = new GithubAuthentication(
                properties, name -> "test-token");

        HttpHeaders headers = new HttpHeaders();
        authentication.configureApi(headers);

        assertEquals("Bearer test-token", headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void rejectsMissingPasswordInUsernamePasswordMode() {
        GithubProperties properties = new GithubProperties();
        properties.setAuthMode("username-password");
        properties.setAuthUsername("git-user");
        properties.setPasswordEnv("GIT_PASSWORD");
        GithubAuthentication authentication = new GithubAuthentication(properties, name -> null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> authentication.configureApi(new HttpHeaders()));

        assertTrue(exception.getMessage().contains("GIT_PASSWORD"));
    }

    private String decodeBasic(String authorization) {
        int prefix = authorization.indexOf("Basic ");
        String encoded = authorization.substring(prefix + "Basic ".length());
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
