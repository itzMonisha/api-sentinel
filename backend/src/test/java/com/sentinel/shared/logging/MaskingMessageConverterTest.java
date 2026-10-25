package com.sentinel.shared.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingMessageConverterTest {

    @Test
    @DisplayName("Redacts JWT access and refresh tokens from log output")
    void shouldRedactJwtTokens() {
        String log = "Processing request for token eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c in security filter";
        String masked = MaskingMessageConverter.mask(log);

        assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");
        assertThat(masked).contains("[REDACTED_JWT]");
    }

    @Test
    @DisplayName("Redacts Bearer authorization header values")
    void shouldRedactBearerToken() {
        String log = "Inbound Authorization header: Bearer ya29.a0AfH6SMCxyz123-very-secret-token";
        String masked = MaskingMessageConverter.mask(log);

        assertThat(masked).doesNotContain("ya29.a0AfH6SMCxyz123-very-secret-token");
        assertThat(masked).contains("Bearer [REDACTED]");
    }

    @Test
    @DisplayName("Redacts passwords and secrets from JSON bodies")
    void shouldRedactJsonPasswords() {
        String log = "User registration payload: {\"email\":\"dev@apisentinel.dev\",\"password\":\"P@ssw0rd123!\",\"apiKey\":\"sk_live_99999\"}";
        String masked = MaskingMessageConverter.mask(log);

        assertThat(masked).doesNotContain("P@ssw0rd123!");
        assertThat(masked).doesNotContain("sk_live_99999");
        assertThat(masked).contains("\"password\": \"[REDACTED]\"");
        assertThat(masked).contains("\"apiKey\": \"[REDACTED]\"");
    }

    @Test
    @DisplayName("Redacts embedded database credentials from JDBC and Postgres connection URLs")
    void shouldRedactDatabaseCredentials() {
        String log = "Connecting to database at jdbc:postgresql://db_user:SuperSecretPassword!@db.internal:5432/api_sentinel";
        String masked = MaskingMessageConverter.mask(log);

        assertThat(masked).doesNotContain("SuperSecretPassword!");
        assertThat(masked).contains("jdbc:postgresql://db_user:[REDACTED]@db.internal:5432/api_sentinel");
    }

    @Test
    @DisplayName("Redacts AWS access keys and RSA private keys")
    void shouldRedactAwsAndPrivateKeys() {
        String log = "Configured AWS_KEY: AKIAIOSFODNN7EXAMPLE and key: -----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0\n-----END RSA PRIVATE KEY-----";
        String masked = MaskingMessageConverter.mask(log);

        assertThat(masked).doesNotContain("AKIAIOSFODNN7EXAMPLE");
        assertThat(masked).doesNotContain("MIIEowIBAAKCAQEA0");
        assertThat(masked).contains("[REDACTED_AWS_KEY]");
        assertThat(masked).contains("[REDACTED_PRIVATE_KEY]");
    }
}
