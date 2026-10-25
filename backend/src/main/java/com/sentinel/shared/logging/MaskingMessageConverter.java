package com.sentinel.shared.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * Logback PatternConverter that automatically intercepts and redacts sensitive data,
 * credentials, authentication tokens, and private keys from application logs.
 * Ensures zero secret leakage in production log streams and container stdout.
 */
public class MaskingMessageConverter extends MessageConverter {

    private static final Pattern JWT_PATTERN = Pattern.compile("ey[a-zA-Z0-9_-]{10,}\\.ey[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]+");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9_.-]+");
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile("(?i)Authorization:\\s*[^\\r\\n,;]+");
    private static final Pattern AWS_KEY_PATTERN = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("-----BEGIN [A-Z ]+PRIVATE KEY-----[^-]+-----END [A-Z ]+PRIVATE KEY-----", Pattern.DOTALL);
    private static final Pattern JSON_SECRET_PATTERN = Pattern.compile("(?i)\"(password|secret|apiKey|token|accessToken|refreshToken|clientSecret)\"\\s*:\\s*\"[^\"]+\"");
    private static final Pattern QUERY_PARAM_SECRET_PATTERN = Pattern.compile("(?i)(password|secret|apiKey|token|accessToken|refreshToken)=[^&\\s]+");
    private static final Pattern COLON_SECRET_PATTERN = Pattern.compile("(?i)(password|secret|apiKey|token|accessToken|refreshToken)\\s*:\\s*([^\\r\\n,;\\s]+)");
    private static final Pattern DB_CREDENTIALS_PATTERN = Pattern.compile("(jdbc:postgresql://|postgres(?:ql)?://)([^:/\\s]+):([^@\\s]+)@");

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        return mask(message);
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        String masked = message;
        masked = JWT_PATTERN.matcher(masked).replaceAll("[REDACTED_JWT]");
        masked = BEARER_PATTERN.matcher(masked).replaceAll("Bearer [REDACTED]");
        masked = AUTH_HEADER_PATTERN.matcher(masked).replaceAll("Authorization: [REDACTED]");
        masked = AWS_KEY_PATTERN.matcher(masked).replaceAll("[REDACTED_AWS_KEY]");
        masked = PRIVATE_KEY_PATTERN.matcher(masked).replaceAll("[REDACTED_PRIVATE_KEY]");
        masked = JSON_SECRET_PATTERN.matcher(masked).replaceAll("\"$1\": \"[REDACTED]\"");
        masked = QUERY_PARAM_SECRET_PATTERN.matcher(masked).replaceAll("$1=[REDACTED]");
        masked = COLON_SECRET_PATTERN.matcher(masked).replaceAll("$1: [REDACTED]");
        masked = DB_CREDENTIALS_PATTERN.matcher(masked).replaceAll("$1$2:[REDACTED]@");

        return masked;
    }
}
