package com.sentinel.modules.scanner.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.scanner.entity.Confidence;
import com.sentinel.modules.scanner.entity.SecurityFinding;
import com.sentinel.modules.scanner.entity.SecurityScan;
import com.sentinel.modules.scanner.entity.Severity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Check 5: OpenAPI operations documented without expected security requirements.
 */
@Component
public class OpenApiSecurityRequirementCheck implements ScannerCheck {

    private static final Set<String> SENSITIVE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private final ObjectMapper objectMapper;

    public OpenApiSecurityRequirementCheck(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "OPENAPI_MISSING_SECURITY";
    }

    @Override
    public String getRuleName() {
        return "OpenAPI Operation Missing Security Requirements";
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

        String method = endpoint.getHttpMethod() != null ? endpoint.getHttpMethod().toUpperCase(Locale.ROOT) : "GET";
        String lowerUrl = endpoint.getUrl().toLowerCase(Locale.ROOT);

        boolean isMutating = SENSITIVE_METHODS.contains(method);
        boolean isSensitivePath = lowerUrl.contains("/admin") || lowerUrl.contains("/user")
                || lowerUrl.contains("/account") || lowerUrl.contains("/billing")
                || lowerUrl.contains("/keys") || lowerUrl.contains("/tokens") || lowerUrl.contains("/secrets");

        if (!isMutating && !isSensitivePath) {
            return findings;
        }

        // Check if authConfig is configured
        boolean hasAuthConfig = false;
        if (endpoint.getAuthConfig() != null && !endpoint.getAuthConfig().isBlank()) {
            try {
                JsonNode auth = objectMapper.readTree(endpoint.getAuthConfig());
                String type = auth.has("type") ? auth.get("type").asText() : "";
                if (!type.isBlank() && !type.equalsIgnoreCase("NONE")) {
                    hasAuthConfig = true;
                }
            } catch (Exception ignored) {
            }
        }

        if (!hasAuthConfig) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "OPENAPI_UNSECURED_MUTATING_OPERATION",
                    "OpenAPI Operation Documented Without Security Scheme",
                    Severity.MEDIUM,
                    Confidence.POTENTIAL_RISK,
                    getOwaspCategory(),
                    "The OpenAPI operation (" + method + " " + endpoint.getUrl()
                            + ") is documented without any authentication or security requirements on a mutating or sensitive endpoint.",
                    "HTTP Method: " + method + ", Target: " + context.getSanitizer().redact(endpoint.getUrl()) + ", Security: None",
                    "Declare the required security requirement (e.g. 'security: - BearerAuth: []') in the OpenAPI specification and configure corresponding endpoint authentication."
            ));
        }

        return findings;
    }
}
