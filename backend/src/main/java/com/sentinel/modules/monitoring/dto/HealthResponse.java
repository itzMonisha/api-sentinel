package com.sentinel.modules.monitoring.dto;

import java.time.Instant;
import java.util.Map;

public record HealthResponse(
        String status,
        String application,
        String version,
        String environment,
        Instant timestamp,
        long uptimeSeconds,
        Map<String, Object> components
) {}
