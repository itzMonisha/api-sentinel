package com.sentinel.shared.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes 12-factor cloud database URLs (e.g. from Render, Railway, Fly.io, Neon, Supabase, AWS RDS)
 * into standard Spring Boot JDBC DataSource properties.
 *
 * Automatically converts 'postgres://' or 'postgresql://' URIs into 'jdbc:postgresql://' format,
 * extracting embedded credentials so database connections work seamlessly in managed environments.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "apiSentinelDatabaseUrlOverride";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawDatabaseUrl = environment.getProperty("DATABASE_URL");
        if (rawDatabaseUrl == null || rawDatabaseUrl.isBlank()) {
            rawDatabaseUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        }

        if (rawDatabaseUrl == null || rawDatabaseUrl.isBlank()) {
            return;
        }

        Map<String, Object> properties = parseDatabaseUrl(rawDatabaseUrl.trim());
        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
        }
    }

    /**
     * Parses a cloud database URL and returns standard Spring Boot datasource properties.
     */
    public static Map<String, Object> parseDatabaseUrl(String rawUrl) {
        Map<String, Object> properties = new HashMap<>();

        if (rawUrl == null || rawUrl.isBlank()) {
            return properties;
        }

        if (rawUrl.startsWith("jdbc:")) {
            properties.put("spring.datasource.url", rawUrl);
            return properties;
        }

        if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
            try {
                // Ensure URI parsing by normalizing scheme to a standard prefix if needed
                String normalizedUri = rawUrl;
                if (rawUrl.startsWith("postgres://")) {
                    normalizedUri = "postgresql://" + rawUrl.substring("postgres://".length());
                }

                URI uri = URI.create(normalizedUri);
                String userInfo = uri.getUserInfo();
                if (userInfo != null && !userInfo.isEmpty()) {
                    String[] credentials = userInfo.split(":", 2);
                    if (credentials.length > 0 && !credentials[0].isEmpty()) {
                        properties.put("spring.datasource.username", URLDecoder.decode(credentials[0], StandardCharsets.UTF_8));
                    }
                    if (credentials.length > 1) {
                        properties.put("spring.datasource.password", URLDecoder.decode(credentials[1], StandardCharsets.UTF_8));
                    }
                }

                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 5432;
                String path = uri.getPath() != null ? uri.getPath() : "";
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }

                String query = uri.getQuery();
                StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                        .append(host)
                        .append(":")
                        .append(port)
                        .append("/")
                        .append(path);

                if (query != null && !query.isEmpty()) {
                    jdbcUrl.append("?").append(query);
                }

                properties.put("spring.datasource.url", jdbcUrl.toString());
            } catch (Exception e) {
                // Fallback to literal URL wrapped in jdbc: if parsing fails
                properties.put("spring.datasource.url", "jdbc:" + rawUrl);
            }
        }

        return properties;
    }
}
