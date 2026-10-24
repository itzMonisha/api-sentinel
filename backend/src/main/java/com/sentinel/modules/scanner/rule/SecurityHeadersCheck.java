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

/**
 * Check 1: Missing or unexpected security-related HTTP headers.
 */
@Component
public class SecurityHeadersCheck implements ScannerCheck {

    @Override
    public String getRuleId() {
        return "SEC_HEADER_CHECK";
    }

    @Override
    public String getRuleName() {
        return "Security Headers & Fingerprinting Audit";
    }

    @Override
    public String getOwaspCategory() {
        return "API7:2023 Security Misconfiguration";
    }

    @Override
    public List<SecurityFinding> execute(ApiEndpoint endpoint, ScanContext context, SecurityScan scan) {
        List<SecurityFinding> findings = new ArrayList<>();
        if (endpoint.getUrl() == null || endpoint.getUrl().isBlank()) {
            return findings;
        }

        // Send a safe, non-destructive probe (GET or HEAD)
        HttpRequestSnapshot probe = HttpRequestSnapshot.builder()
                .httpMethod("GET")
                .url(endpoint.getUrl())
                .build();

        HttpResponseSnapshot response = context.getHttpClient().execute(probe);
        if (!response.isSuccess() && response.getStatusCode() == 0) {
            return findings; // Target not reachable, skip header check
        }

        boolean isHttps = endpoint.getUrl().toLowerCase(Locale.ROOT).startsWith("https://");

        // 1. Strict-Transport-Security (HSTS)
        if (isHttps) {
            String hsts = response.getFirstHeader("Strict-Transport-Security");
            if (hsts == null || hsts.isBlank()) {
                findings.add(new SecurityFinding(
                        scan,
                        endpoint,
                        "MISSING_HSTS",
                        "Missing HTTP Strict Transport Security (HSTS) Header",
                        Severity.MEDIUM,
                        Confidence.CONFIRMED,
                        getOwaspCategory(),
                        "The API endpoint is served over HTTPS but does not return a Strict-Transport-Security header.",
                        "Strict-Transport-Security header is absent in response from " + endpoint.getUrl(),
                        "Configure HSTS with 'Strict-Transport-Security: max-age=31536000; includeSubDomains; preload'."
                ));
            }
        }

        // 2. X-Content-Type-Options
        String nosniff = response.getFirstHeader("X-Content-Type-Options");
        if (nosniff == null || !nosniff.equalsIgnoreCase("nosniff")) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "MISSING_X_CONTENT_TYPE_OPTIONS",
                    "Missing X-Content-Type-Options Header",
                    Severity.LOW,
                    Confidence.CONFIRMED,
                    getOwaspCategory(),
                    "The response lacks 'X-Content-Type-Options: nosniff', which allows older browsers to perform MIME-type sniffing.",
                    "X-Content-Type-Options: " + (nosniff != null ? nosniff : "absent"),
                    "Set 'X-Content-Type-Options: nosniff' across all API responses."
            ));
        }

        // 3. X-Frame-Options or CSP frame-ancestors
        String xfo = response.getFirstHeader("X-Frame-Options");
        String csp = response.getFirstHeader("Content-Security-Policy");
        boolean hasFrameProtection = (xfo != null && (xfo.equalsIgnoreCase("DENY") || xfo.equalsIgnoreCase("SAMEORIGIN")))
                || (csp != null && csp.toLowerCase(Locale.ROOT).contains("frame-ancestors"));

        if (!hasFrameProtection) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "MISSING_CLICKJACKING_PROTECTION",
                    "Missing Clickjacking Defense (X-Frame-Options)",
                    Severity.LOW,
                    Confidence.CONFIRMED,
                    getOwaspCategory(),
                    "Neither X-Frame-Options nor Content-Security-Policy frame-ancestors was found, potentially allowing embedding in malicious frames.",
                    "X-Frame-Options: " + (xfo != null ? xfo : "absent") + ", CSP: " + (csp != null ? "present but no frame-ancestors" : "absent"),
                    "Configure 'X-Frame-Options: DENY' or 'Content-Security-Policy: frame-ancestors \\'none\\''."
            ));
        }

        // 4. Server Fingerprint / Technology Leakage in headers
        String server = response.getFirstHeader("Server");
        String poweredBy = response.getFirstHeader("X-Powered-By");

        if (server != null && (server.matches(".*\\d.*") || server.toLowerCase(Locale.ROOT).contains("apache") || server.toLowerCase(Locale.ROOT).contains("nginx"))) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "SERVER_BANNER_LEAKAGE",
                    "Detailed Server Software Fingerprint Exposed",
                    Severity.LOW,
                    Confidence.CONFIRMED,
                    "API8:2023 Security Misconfiguration",
                    "The 'Server' response header exposes granular backend software versions, aiding attacker reconnaissance.",
                    context.getSanitizer().redact("Server: " + server),
                    "Suppress or genericize the 'Server' header in reverse proxies and web servers (e.g. server_tokens off in Nginx)."
            ));
        }

        if (poweredBy != null && !poweredBy.isBlank()) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "X_POWERED_BY_LEAKAGE",
                    "Backend Technology Banner Exposed (X-Powered-By)",
                    Severity.LOW,
                    Confidence.CONFIRMED,
                    "API8:2023 Security Misconfiguration",
                    "The 'X-Powered-By' header exposes backend runtime/framework info (e.g. Express, PHP, ASP.NET).",
                    context.getSanitizer().redact("X-Powered-By: " + poweredBy),
                    "Disable the 'X-Powered-By' response header in your application framework configuration."
            ));
        }

        return findings;
    }
}
