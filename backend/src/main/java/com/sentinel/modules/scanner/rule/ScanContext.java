package com.sentinel.modules.scanner.rule;

import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.scanner.service.Sanitizer;
import com.sentinel.shared.net.SafeHttpClient;
import com.sentinel.shared.security.UserPrincipal;

import java.util.Collections;
import java.util.Map;

public class ScanContext {

    private final Project project;
    private final SafeHttpClient httpClient;
    private final UserPrincipal principal;
    private final Map<String, String> testFixtureAuth;
    private final Sanitizer sanitizer;

    public ScanContext(
            Project project,
            SafeHttpClient httpClient,
            UserPrincipal principal,
            Map<String, String> testFixtureAuth,
            Sanitizer sanitizer) {
        this.project = project;
        this.httpClient = httpClient;
        this.principal = principal;
        this.testFixtureAuth = testFixtureAuth != null ? testFixtureAuth : Collections.emptyMap();
        this.sanitizer = sanitizer;
    }

    public Project getProject() {
        return project;
    }

    public SafeHttpClient getHttpClient() {
        return httpClient;
    }

    public UserPrincipal getPrincipal() {
        return principal;
    }

    public Map<String, String> getTestFixtureAuth() {
        return testFixtureAuth;
    }

    public Sanitizer getSanitizer() {
        return sanitizer;
    }
}
