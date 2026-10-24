package com.sentinel.modules.monitoring.dto;

import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.monitoring.entity.HealthCheckStatus;

import java.time.Instant;
import java.util.UUID;

public class HealthCheckResponse {

    private UUID id;
    private UUID projectId;
    private UUID endpointId;
    private String endpointName;
    private String endpointUrl;
    private String httpMethod;
    private String name;
    private int intervalSeconds;
    private int timeoutMs;
    private int expectedStatus;
    private int failureThreshold;
    private int recoveryThreshold;
    private int consecutiveFailures;
    private int consecutiveSuccesses;
    private boolean active;
    private HealthCheckStatus lastStatus;
    private Instant lastCheckedAt;
    private Instant nextCheckAt;
    private HealthMetricsDto metrics;
    private Instant createdAt;

    public HealthCheckResponse() {
    }

    public static HealthCheckResponse fromEntity(HealthCheck entity, HealthMetricsDto metrics) {
        HealthCheckResponse res = new HealthCheckResponse();
        res.id = entity.getId();
        res.projectId = entity.getProject() != null ? entity.getProject().getId() : null;
        if (entity.getEndpoint() != null) {
            res.endpointId = entity.getEndpoint().getId();
            res.endpointName = entity.getEndpoint().getName();
            res.endpointUrl = entity.getEndpoint().getUrl();
            res.httpMethod = entity.getEndpoint().getHttpMethod();
        }
        res.name = entity.getName();
        res.intervalSeconds = entity.getIntervalSeconds();
        res.timeoutMs = entity.getTimeoutMs();
        res.expectedStatus = entity.getExpectedStatus();
        res.failureThreshold = entity.getFailureThreshold();
        res.recoveryThreshold = entity.getRecoveryThreshold();
        res.consecutiveFailures = entity.getConsecutiveFailures();
        res.consecutiveSuccesses = entity.getConsecutiveSuccesses();
        res.active = entity.isActive();
        res.lastStatus = entity.getLastStatus();
        res.lastCheckedAt = entity.getLastCheckedAt();
        res.nextCheckAt = entity.getNextCheckAt();
        res.metrics = metrics;
        res.createdAt = entity.getCreatedAt();
        return res;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getName() {
        return name;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getExpectedStatus() {
        return expectedStatus;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public int getRecoveryThreshold() {
        return recoveryThreshold;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public int getConsecutiveSuccesses() {
        return consecutiveSuccesses;
    }

    public boolean isActive() {
        return active;
    }

    public HealthCheckStatus getLastStatus() {
        return lastStatus;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getNextCheckAt() {
        return nextCheckAt;
    }

    public HealthMetricsDto getMetrics() {
        return metrics;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
