package com.sentinel.modules.monitoring.controller;

import com.sentinel.modules.monitoring.dto.HealthResponse;
import com.sentinel.shared.model.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health & System", description = "System health, liveness and readiness probes")
public class HealthController {

    private final DataSource dataSource;

    @Value("${spring.application.name:api-sentinel}")
    private String applicationName;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Autowired
    private org.springframework.core.env.Environment environment;

    public HealthController(@Autowired(required = false) DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    @Operation(summary = "Get System Health Status", description = "Returns system uptime, active profile, and database readiness")
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth() {
        Map<String, Object> components = new HashMap<>();
        boolean dbHealthy = checkDatabaseHealth(components);

        String overallStatus = dbHealthy ? "UP" : "DEGRADED";
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        String resolvedProfile = (environment != null && environment.getActiveProfiles().length > 0)
                ? environment.getActiveProfiles()[0]
                : activeProfile;

        HealthResponse health = new HealthResponse(
                overallStatus,
                applicationName,
                "0.0.1-SNAPSHOT",
                resolvedProfile,
                Instant.now(),
                uptimeSeconds,
                components
        );

        return ResponseEntity.ok(ApiResponse.ok(health, "API Sentinel is operational"));
    }

    private boolean checkDatabaseHealth(Map<String, Object> components) {
        if (dataSource == null) {
            components.put("database", Map.of("status", "DISABLED", "message", "No DataSource configured"));
            return true;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            boolean valid = conn.isValid(2);
            components.put("database", Map.of(
                    "status", valid ? "UP" : "DOWN",
                    "databaseProductName", conn.getMetaData().getDatabaseProductName(),
                    "databaseProductVersion", conn.getMetaData().getDatabaseProductVersion()
            ));
            return valid;
        } catch (Exception e) {
            components.put("database", Map.of(
                    "status", "DOWN",
                    "error", e.getMessage() != null ? e.getMessage() : "Connection failed"
            ));
            return false;
        }
    }
}
