package com.sentinel.modules.scanner.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.scanner.entity.Confidence;
import com.sentinel.modules.scanner.entity.SecurityFinding;
import com.sentinel.modules.scanner.entity.SecurityScan;
import com.sentinel.modules.scanner.entity.Severity;
import com.sentinel.shared.net.HttpRequestSnapshot;
import com.sentinel.shared.net.HttpResponseSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Check 3: Publicly accessible endpoints that the user has explicitly marked as expected to require authentication.
 */
@Component
public class UnauthenticatedAccessCheck implements ScannerCheck {

    private final ObjectMapper objectMapper;

    public UnauthenticatedAccessCheck(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "UNAUTHENTICATED_ACCESS";
    }

    @Override
    public String getRuleName() {
        return "Unauthenticated Sensitive Endpoint Exposure";
    }

    @Override
    public String getOwaspCategory() {
        return "API2:2023 Broken Authentication";
    }

    @Override
    public List<SecurityFinding> execute(ApiEndpoint endpoint, ScanContext context, SecurityScan scan) {
        List<SecurityFinding> findings = new ArrayList<>();
        if (endpoint.getUrl() == null || endpoint.getUrl().isBlank()) {
            return findings;
        }

        boolean expectedToRequireAuth = false;
        String authReason = "";

        // Check 1: User explicitly configured authConfig with type != NONE
        if (endpoint.getAuthConfig() != null && !endpoint.getAuthConfig().isBlank()) {
            try {
                JsonNode authNode = objectMapper.readTree(endpoint.getAuthConfig());
                String type = authNode.has("type") ? authNode.get("type").asText() : "";
                if (!type.isBlank() && !type.equalsIgnoreCase("NONE")) {
                    expectedToRequireAuth = true;
                    authReason = "Configured AuthConfig specifies " + type + " authentication";
                }
            } catch (Exception ignored) {
            }
        }

        // Check 2: Path or name indicates protected/sensitive resource
        String lowerUrl = endpoint.getUrl().toLowerCase(Locale.ROOT);
        if (!expectedToRequireAuth && (lowerUrl.contains("/admin") || lowerUrl.contains("/private")
                || lowerUrl.contains("/me") || lowerUrl.contains("/account") || lowerUrl.contains("/billing"))) {
            expectedToRequireAuth = true;
            authReason = "Path contains sensitive designation requiring access restriction";
        }

        if (!expectedToRequireAuth) {
            return findings;
        }

        // Send a probe strictly WITHOUT authentication credentials
        HttpRequestSnapshot probe = HttpRequestSnapshot.builder()
                .httpMethod("GET")
                .url(endpoint.getUrl())
                .headers(Collections.emptyMap())
                .build();

        HttpResponseSnapshot response = context.getHttpClient().execute(probe);
        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            String excerpt = response.getBody() != null ? response.getBody().trim() : "";
            if (excerpt.length() > 200) {
                excerpt = excerpt.substring(0, 200) + "...";
            }

            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "PUBLIC_SENSITIVE_ENDPOINT",
                    "Protected Endpoint Accessible Without Authentication",
                    Severity.HIGH,
                    Confidence.CONFIRMED,
                    getOwaspCategory(),
                    "The endpoint is designated as requiring authentication (" + authReason + "), but returned HTTP "
                            + response.getStatusCode() + " to an unauthenticated request.",
                    context.getSanitizer().redact("Status: " + response.getStatusCode() + ", Body Excerpt: " + excerpt),
                    "Configure security filter chain / authentication interceptor to enforce valid authorization headers and reject unauthenticated requests with HTTP 401 Unauthorized."
            ));
        }

        return findings;
    }
}
