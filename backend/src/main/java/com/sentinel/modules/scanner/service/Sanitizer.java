package com.sentinel.modules.scanner.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Redacts secrets, personal data (PII), and sensitive infrastructure paths
 * from scanner evidence to ensure zero data leakage.
 */
@Component
public class Sanitizer {

    private static final Pattern JWT_PATTERN = Pattern.compile("ey[a-zA-Z0-9_-]{10,}\\.ey[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]+");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9_.-]+");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("-----BEGIN [A-Z ]+PRIVATE KEY-----[^-]+-----END [A-Z ]+PRIVATE KEY-----", Pattern.DOTALL);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+");
    private static final Pattern PASSWORD_JSON_PATTERN = Pattern.compile("(?i)\"(password|secret|apiKey|token|accessToken)\"\\s*:\\s*\"[^\"]+\"");
    private static final Pattern WINDOWS_PATH_PATTERN = Pattern.compile("[a-zA-Z]:\\\\[a-zA-Z0-9_\\-.\\\\]+");
    private static final Pattern UNIX_PATH_PATTERN = Pattern.compile("/(?:home|var|etc|usr|app|root)/[a-zA-Z0-9_\\-./]+");

    public String redact(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String result = input;
        result = JWT_PATTERN.matcher(result).replaceAll("[REDACTED_JWT]");
        result = BEARER_PATTERN.matcher(result).replaceAll("Bearer [REDACTED]");
        result = AWS_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_AWS_KEY]");
        result = PRIVATE_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_PRIVATE_KEY]");
        result = EMAIL_PATTERN.matcher(result).replaceAll("[REDACTED_EMAIL]");
        result = PASSWORD_JSON_PATTERN.matcher(result).replaceAll("\"$1\": \"[REDACTED]\"");
        result = WINDOWS_PATH_PATTERN.matcher(result).replaceAll("[REDACTED_PATH]");
        result = UNIX_PATH_PATTERN.matcher(result).replaceAll("[REDACTED_PATH]");

        // Cap length of evidence at 2048 chars to avoid bloated DB records
        if (result.length() > 2048) {
            result = result.substring(0, 2048) + "... [truncated]";
        }

        return result;
    }
}
