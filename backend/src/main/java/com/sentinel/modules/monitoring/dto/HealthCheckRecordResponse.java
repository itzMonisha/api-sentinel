package com.sentinel.modules.monitoring.dto;

import com.sentinel.modules.monitoring.entity.HealthCheckRecord;

import java.time.Instant;
import java.util.UUID;

public class HealthCheckRecordResponse {

    private UUID id;
    private UUID healthCheckId;
    private UUID endpointId;
    private Integer statusCode;
    private long responseTimeMs;
    private boolean success;
    private String errorMessage;
    private Instant executedAt;

    public HealthCheckRecordResponse() {
    }

    public static HealthCheckRecordResponse fromEntity(HealthCheckRecord record) {
        HealthCheckRecordResponse res = new HealthCheckRecordResponse();
        res.id = record.getId();
        res.healthCheckId = record.getHealthCheck() != null ? record.getHealthCheck().getId() : null;
        res.endpointId = record.getEndpoint() != null ? record.getEndpoint().getId() : null;
        res.statusCode = record.getStatusCode();
        res.responseTimeMs = record.getResponseTimeMs();
        res.success = record.isSuccess();
        res.errorMessage = record.getErrorMessage();
        res.executedAt = record.getExecutedAt();
        return res;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHealthCheckId() {
        return healthCheckId;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
