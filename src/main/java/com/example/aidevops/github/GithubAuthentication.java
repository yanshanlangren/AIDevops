package com.example.aidevops.github;

import com.example.aidevops.config.GithubProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GithubAuthentication {
    private static final String TOKEN_MODE = "token";
    private static final String USERNAME_PASSWORD_MODE = "username-password";

    private final GithubProperties github;
    private final Function<String, String> environment;

    @Autowired
    public GithubAuthentication(GithubProperties github) {
        this(github, new Function<String, String>() {
            @Override
            public String apply(String name) {
                return System.getenv(name);
            }
        });
    }

    GithubAuthentication(GithubProperties github, Function<String, String> environment) {
        this.github = github;
        this.environment = environment;
    }

    public void configureGit(ProcessBuilder builder, boolean required) {
        Credentials credentials = resolve(required);
        if (credentials == null) {
            return;
        }
        String value = credentials.username + ":" + credentials.secret;
        String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        builder.environment().put("GIT_CONFIG_COUNT", "1");
        builder.environment().put("GIT_CONFIG_KEY_0", "http.extraHeader");
        builder.environment().put("GIT_CONFIG_VALUE_0", "Authorization: Basic " + encoded);
    }

    public void configureApi(HttpHeaders headers) {
        Credentials credentials = resolve(true);
        if (TOKEN_MODE.equals(credentials.mode)) {
            headers.setBearerAuth(credentials.secret);
        } else {
            headers.setBasicAuth(credentials.username, credentials.secret, StandardCharsets.UTF_8);
        }
    }

    private Credentials resolve(boolean required) {
        String mode = normalizedMode();
        if (TOKEN_MODE.equals(mode)) {
            String token = readEnvironment(github.getTokenEnv());
            if (!StringUtils.hasText(token)) {
                if (!required) {
                    return null;
                }
                throw new IllegalStateException(
                        "Missing GitHub token environment variable: " + github.getTokenEnv());
            }
            return new Credentials(mode, "x-access-token", token);
        }

        String username = github.getAuthUsername();
        String password = readEnvironment(github.getPasswordEnv());
        if (!StringUtils.hasText(username) && !StringUtils.hasText(password) && !required) {
            return null;
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("github.auth-username must be configured for username-password mode");
        }
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "Missing Git password environment variable: " + github.getPasswordEnv());
        }
        return new Credentials(mode, username, password);
    }

    private String normalizedMode() {
        String mode = github.getAuthMode();
        if (TOKEN_MODE.equalsIgnoreCase(mode)) {
            return TOKEN_MODE;
        }
        if (USERNAME_PASSWORD_MODE.equalsIgnoreCase(mode)) {
            return USERNAME_PASSWORD_MODE;
        }
        throw new IllegalStateException("Unsupported github.auth-mode: " + mode);
    }

    private String readEnvironment(String name) {
        return StringUtils.hasText(name) ? environment.apply(name) : null;
    }

    private static class Credentials {
        private final String mode;
        private final String username;
        private final String secret;

        private Credentials(String mode, String username, String secret) {
            this.mode = mode;
            this.username = username;
            this.secret = secret;
        }
    }
}
