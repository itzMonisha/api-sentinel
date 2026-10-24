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
import java.util.List;
import java.util.Map;

/**
 * Check 4: Authentication configuration inconsistencies and token validation robustness.
 */
@Component
public class AuthenticationConsistencyCheck implements ScannerCheck {

    private final ObjectMapper objectMapper;

    public AuthenticationConsistencyCheck(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "AUTH_CONFIG_INCONSISTENCY";
    }

    @Override
    public String getRuleName() {
        return "Authentication Scheme Consistency & Token Validation Robustness";
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

        // 1. Static Configuration Consistency Check
        if (endpoint.getAuthConfig() != null && !endpoint.getAuthConfig().isBlank()) {
            try {
                JsonNode authNode = objectMapper.readTree(endpoint.getAuthConfig());
                String type = authNode.has("type") ? authNode.get("type").asText() : "";

                if ("API_KEY".equalsIgnoreCase(type)) {
                    String headerName = authNode.has("headerName") ? authNode.get("headerName").asText() : "";
                    String keyName = authNode.has("keyName") ? authNode.get("keyName").asText() : "";
                    if (headerName.isBlank() && keyName.isBlank()) {
                        findings.add(new SecurityFinding(
                                scan,
                                endpoint,
                                "INCOMPLETE_API_KEY_CONFIG",
                                "API Key Authentication Missing Header/Parameter Name",
                                Severity.LOW,
                                Confidence.CONFIRMED,
                                getOwaspCategory(),
                                "Endpoint is configured for API Key authentication, but neither a header name nor query parameter key was defined.",
                                "AuthConfig: " + context.getSanitizer().redact(endpoint.getAuthConfig()),
                                "Specify the exact HTTP header name (e.g. X-API-Key) or query parameter name for the API key in the endpoint configuration."
                        ));
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 2. Invalid Token Handling Probe
        HttpRequestSnapshot invalidTokenProbe = HttpRequestSnapshot.builder()
                .httpMethod("GET")
                .url(endpoint.getUrl())
                .headers(Map.of("Authorization", "Bearer invalid_probe_sentinel_token"))
                .build();

        HttpResponseSnapshot response = context.getHttpClient().execute(invalidTokenProbe);

        if (response.getStatusCode() == 500) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "MALFORMED_TOKEN_UNHANDLED_EXCEPTION",
                    "Unhandled Server Exception on Malformed Authentication Header",
                    Severity.MEDIUM,
                    Confidence.CONFIRMED,
                    getOwaspCategory(),
                    "Submitting an invalid authorization token caused the backend to crash with HTTP 500 Internal Server Error instead of returning HTTP 401 Unauthorized.",
                    "HTTP Status: 500 Internal Server Error in response to invalid Bearer token probe",
                    "Catch token parsing and signature verification exceptions in the authentication filter and respond gracefully with HTTP 401 Unauthorized."
            ));
        }

        return findings;
    }
}
