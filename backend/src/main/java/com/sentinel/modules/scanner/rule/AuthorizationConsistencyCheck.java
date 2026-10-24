package com.sentinel.modules.scanner.rule;

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
import java.util.Locale;
import java.util.Map;

/**
 * Check 7: Inconsistent authorization behavior evaluated only when the project owner
 * provides a safe, explicitly configured test fixture or approved low-privilege test account.
 */
@Component
public class AuthorizationConsistencyCheck implements ScannerCheck {

    @Override
    public String getRuleId() {
        return "INCONSISTENT_AUTHORIZATION";
    }

    @Override
    public String getRuleName() {
        return "Function-Level Authorization & Privilege Separation Audit";
    }

    @Override
    public String getOwaspCategory() {
        return "API5:2023 Broken Function Level Authorization";
    }

    @Override
    public List<SecurityFinding> execute(ApiEndpoint endpoint, ScanContext context, SecurityScan scan) {
        List<SecurityFinding> findings = new ArrayList<>();
        if (endpoint.getUrl() == null || endpoint.getUrl().isBlank()) {
            return findings;
        }

        // Safety invariant: Execute ONLY when the owner explicitly configured a safe test fixture
        Map<String, String> testFixtureAuth = context.getTestFixtureAuth();
        if (testFixtureAuth == null || testFixtureAuth.isEmpty()) {
            return findings; // Skip safely without guessing credentials or sending unapproved tokens
        }

        String lowerUrl = endpoint.getUrl().toLowerCase(Locale.ROOT);
        String desc = endpoint.getDescription() != null ? endpoint.getDescription().toLowerCase(Locale.ROOT) : "";

        boolean isElevatedEndpoint = lowerUrl.contains("/admin") || lowerUrl.contains("/manage")
                || lowerUrl.contains("/roles") || lowerUrl.contains("/system") || desc.contains("admin only")
                || desc.contains("owner only");

        if (!isElevatedEndpoint) {
            return findings;
        }

        // Send a probe using the owner's explicitly provided low-privilege test account fixture
        HttpRequestSnapshot probe = HttpRequestSnapshot.builder()
                .httpMethod("GET")
                .url(endpoint.getUrl())
                .headers(testFixtureAuth)
                .build();

        HttpResponseSnapshot response = context.getHttpClient().execute(probe);

        // If the elevated endpoint returns 200 OK to the low-privilege test account, BFLA is confirmed
        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "BROKEN_FUNCTION_LEVEL_AUTH",
                    "Elevated Endpoint Accessible to Low-Privilege Test Account",
                    Severity.HIGH,
                    Confidence.CONFIRMED,
                    getOwaspCategory(),
                    "The administrative endpoint responded with HTTP " + response.getStatusCode()
                            + " when accessed using the configured low-privilege test account fixture.",
                    "Status: " + response.getStatusCode() + ", Headers: " + context.getSanitizer().redact(testFixtureAuth.toString()),
                    "Enforce role-based access control (RBAC) checks to reject non-administrative users with HTTP 403 Forbidden."
            ));
        }

        return findings;
    }
}
