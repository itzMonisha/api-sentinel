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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Check 6: Response errors or metadata that reveal potentially sensitive information,
 * using conservative detection rules to avoid false positives.
 */
@Component
public class SensitiveDataExposureCheck implements ScannerCheck {

    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "(?i)(at\\s+[a-zA-Z0-9_$.]+\\([a-zA-Z0-9_]+\\.java:\\d+\\)|Exception in thread|Traceback \\(most recent call last\\)|NullPointerException|SQLException|org\\.hibernate|org\\.springframework\\.web)"
    );

    private static final Pattern SQL_ERROR_PATTERN = Pattern.compile(
            "(?i)(syntax error at or near|pg_catalog|ORA-\\d{5}|SQLState|MySQLSyntaxErrorException|SQLite3::SQLException|com\\.mysql\\.cj\\.jdbc)"
    );

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN [A-Z ]+PRIVATE KEY-----"
    );

    private static final Pattern INTERNAL_PATH_PATTERN = Pattern.compile(
            "(?:[a-zA-Z]:\\\\(?:Users|app|src|var|workspace)|/(?:home|var/log|etc/passwd|usr/src|app/src))/[a-zA-Z0-9_\\-./\\\\]+"
    );

    @Override
    public String getRuleId() {
        return "SENSITIVE_DATA_EXPOSURE";
    }

    @Override
    public String getRuleName() {
        return "Information Disclosure & Stack Trace Leakage";
    }

    @Override
    public String getOwaspCategory() {
        return "API8:2023 Security Misconfiguration";
    }

    @Override
    public List<SecurityFinding> execute(ApiEndpoint endpoint, ScanContext context, SecurityScan scan) {
        List<SecurityFinding> findings = new ArrayList<>();
        if (endpoint.getUrl() == null || endpoint.getUrl().isBlank()) {
            return findings;
        }

        // Send a safe probe with a non-destructive malformed parameter to inspect error handling
        String probeUrl = endpoint.getUrl().contains("?")
                ? endpoint.getUrl() + "&__sentinel_probe_param=test"
                : endpoint.getUrl() + "?__sentinel_probe_param=test";

        HttpRequestSnapshot probe = HttpRequestSnapshot.builder()
                .httpMethod("GET")
                .url(probeUrl)
                .build();

        HttpResponseSnapshot response = context.getHttpClient().execute(probe);
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            return findings;
        }

        // 1. Check for Leaked Private Keys (CRITICAL)
        if (PRIVATE_KEY_PATTERN.matcher(body).find()) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "PRIVATE_KEY_EXPOSURE",
                    "Private Cryptographic Key Material Disclosed in Response",
                    Severity.CRITICAL,
                    Confidence.CONFIRMED,
                    "API3:2023 Broken Object Property Level Authorization",
                    "The response body contains raw private cryptographic key headers.",
                    context.getSanitizer().redact(extractSnippet(body, PRIVATE_KEY_PATTERN)),
                    "Immediately revoke exposed private keys and ensure private cryptographic material is never serialized into API responses."
            ));
        }

        // 2. Check for SQL Syntax / Database Errors (HIGH)
        Matcher sqlMatcher = SQL_ERROR_PATTERN.matcher(body);
        if (sqlMatcher.find()) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "DATABASE_ERROR_EXPOSURE",
                    "Raw Database Exception and Schema Information Disclosed",
                    Severity.HIGH,
                    Confidence.CONFIRMED,
                    getOwaspCategory(),
                    "The response exposes raw SQL error messages or database catalog metadata, aiding SQL injection reconnaissance.",
                    context.getSanitizer().redact(extractSnippet(body, SQL_ERROR_PATTERN)),
                    "Configure database abstraction layers and global error handlers to mask underlying SQL exceptions."
            ));
        }

        // 3. Check for Stack Traces (MEDIUM)
        Matcher stackMatcher = STACK_TRACE_PATTERN.matcher(body);
        if (stackMatcher.find()) {
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "STACK_TRACE_LEAKAGE",
                    "Application Stack Trace Disclosed in Error Response",
                    Severity.MEDIUM,
                    Confidence.CONFIRMED,
                    getOwaspCategory(),
                    "The API response returned a raw application stack trace disclosing internal classes, file paths, and line numbers.",
                    context.getSanitizer().redact(extractSnippet(body, STACK_TRACE_PATTERN)),
                    "Configure server.error.include-stacktrace=never and handle uncaught exceptions with RFC 7807 Problem Details."
            ));
        }

        // 4. Check for Internal Filesystem Paths (LOW)
        Matcher pathMatcher = INTERNAL_PATH_PATTERN.matcher(body);
        if (pathMatcher.find() && !stackMatcher.find()) { // avoid duplicate if already caught by stack trace
            findings.add(new SecurityFinding(
                    scan,
                    endpoint,
                    "INTERNAL_PATH_DISCLOSURE",
                    "Internal Server Filesystem Path Disclosed",
                    Severity.LOW,
                    Confidence.CONFIRMED,
                    getOwaspCategory(),
                    "The API response discloses internal server directories or file system paths.",
                    context.getSanitizer().redact(extractSnippet(body, INTERNAL_PATH_PATTERN)),
                    "Sanitize file path references in error messages and API responses."
            ));
        }

        return findings;
    }

    private String extractSnippet(String body, Pattern pattern) {
        Matcher m = pattern.matcher(body);
        if (m.find()) {
            int start = Math.max(0, m.start() - 30);
            int end = Math.min(body.length(), m.end() + 70);
            return body.substring(start, end).replaceAll("[\\r\\n]+", " ");
        }
        return body.length() > 100 ? body.substring(0, 100) : body;
    }
}
