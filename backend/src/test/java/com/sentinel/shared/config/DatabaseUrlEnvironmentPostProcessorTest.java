package com.sentinel.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    @DisplayName("Converts standard postgres:// cloud URL to Spring Boot JDBC properties with decoded credentials")
    void shouldConvertPostgresUri() {
        String raw = "postgres://sentinel_prod_user:secret%40pass123@db.cloudprovider.internal:5432/api_sentinel?sslmode=require";
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.parseDatabaseUrl(raw);

        assertThat(props)
                .containsEntry("spring.datasource.url", "jdbc:postgresql://db.cloudprovider.internal:5432/api_sentinel?sslmode=require")
                .containsEntry("spring.datasource.username", "sentinel_prod_user")
                .containsEntry("spring.datasource.password", "secret@pass123");
    }

    @Test
    @DisplayName("Converts postgresql:// URI without port to standard 5432 default")
    void shouldConvertPostgresqlUriWithDefaultPort() {
        String raw = "postgresql://neon_admin:StrongPassword!@ep-cool-project.us-east-2.aws.neon.tech/neondb";
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.parseDatabaseUrl(raw);

        assertThat(props)
                .containsEntry("spring.datasource.url", "jdbc:postgresql://ep-cool-project.us-east-2.aws.neon.tech:5432/neondb")
                .containsEntry("spring.datasource.username", "neon_admin")
                .containsEntry("spring.datasource.password", "StrongPassword!");
    }

    @Test
    @DisplayName("Passes through standard jdbc: URL without modification")
    void shouldPassThroughJdbcUrl() {
        String raw = "jdbc:postgresql://rds.amazonaws.com:5432/api_sentinel?sslmode=verify-full";
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.parseDatabaseUrl(raw);

        assertThat(props)
                .containsEntry("spring.datasource.url", raw)
                .doesNotContainKey("spring.datasource.username");
    }

    @Test
    @DisplayName("Environment post-processor injects highest precedence property source when DATABASE_URL is set")
    void shouldInjectPropertySourceIntoEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DATABASE_URL", "postgres://appuser:p%23ss@postgres.internal:5432/sentinel_db");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://postgres.internal:5432/sentinel_db");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("appuser");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p#ss");
    }

    @Test
    @DisplayName("Does nothing when no DATABASE_URL or SPRING_DATASOURCE_URL is present")
    void shouldDoNothingWhenNoUrlConfigured() {
        MockEnvironment env = new MockEnvironment();
        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url")).isNull();
        assertThat(env.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }
}
