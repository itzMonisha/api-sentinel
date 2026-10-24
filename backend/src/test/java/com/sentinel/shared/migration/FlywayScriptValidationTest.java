package com.sentinel.shared.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayScriptValidationTest {

    private static final Pattern MIGRATION_PATTERN = Pattern.compile("^V(\\d+)__([a-zA-Z0-9_]+)\\.sql$");

    @Test
    @DisplayName("Verify that all Flyway migration scripts exist, follow naming rules, and contain valid SQL")
    void shouldValidateFlywayMigrationScripts() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:db/migration/*.sql");

        assertThat(resources).isNotEmpty();

        Set<Integer> versions = new HashSet<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            assertThat(filename).isNotNull();

            Matcher matcher = MIGRATION_PATTERN.matcher(filename);
            assertThat(matcher.matches())
                    .withFailMessage("Flyway script '%s' does not match naming convention V<number>__<description>.sql", filename)
                    .isTrue();

            int version = Integer.parseInt(matcher.group(1));
            assertThat(versions.add(version))
                    .withFailMessage("Duplicate Flyway version number detected: %d in file %s", version, filename)
                    .isTrue();

            try (InputStream in = resource.getInputStream()) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                assertThat(content)
                        .withFailMessage("Flyway script '%s' is empty", filename)
                        .isNotEmpty();
                assertThat(content.toUpperCase())
                        .withFailMessage("Flyway script '%s' does not contain standard DDL statements", filename)
                        .contains("CREATE");
            }
        }
    }
}
