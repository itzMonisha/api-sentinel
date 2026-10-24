package com.sentinel.modules.monitoring.dto;

import com.sentinel.modules.monitoring.entity.Incident;
import com.sentinel.modules.monitoring.entity.IncidentSeverity;
import com.sentinel.modules.monitoring.entity.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public class IncidentResponse {

    private UUID id;
    private UUID projectId;
    private String projectName;
    private UUID endpointId;
    private String endpointName;
    private String endpointUrl;
    private String httpMethod;
    private UUID healthCheckId;
    private String title;
    private String description;
    private IncidentSeverity severity;
    private IncidentStatus status;
    private int failureCount;
    private String rootCause;
    private Instant startedAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    private String acknowledgedByName;
    private String resolvedByName;
    private Instant createdAt;

    public IncidentResponse() {
    }

    public static IncidentResponse fromEntity(Incident incident) {
        IncidentResponse res = new IncidentResponse();
        res.id = incident.getId();
        if (incident.getProject() != null) {
            res.projectId = incident.getProject().getId();
            res.projectName = incident.getProject().getName();
        }
        if (incident.getEndpoint() != null) {
            res.endpointId = incident.getEndpoint().getId();
            res.endpointName = incident.getEndpoint().getName();
            res.endpointUrl = incident.getEndpoint().getUrl();
            res.httpMethod = incident.getEndpoint().getHttpMethod();
        }
        res.healthCheckId = incident.getHealthCheck() != null ? incident.getHealthCheck().getId() : null;
        res.title = incident.getTitle();
        res.description = incident.getDescription();
        res.severity = incident.getSeverity();
        res.status = incident.getStatus();
        res.failureCount = incident.getFailureCount();
        res.rootCause = incident.getRootCause();
        res.startedAt = incident.getStartedAt();
        res.acknowledgedAt = incident.getAcknowledgedAt();
        res.resolvedAt = incident.getResolvedAt();
        res.acknowledgedByName = incident.getAcknowledgedBy() != null ? incident.getAcknowledgedBy().getFullName() : null;
        res.resolvedByName = incident.getResolvedBy() != null ? incident.getResolvedBy().getFullName() : null;
        res.createdAt = incident.getCreatedAt();
        return res;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
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

    public UUID getHealthCheckId() {
        return healthCheckId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public String getRootCause() {
        return rootCause;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getAcknowledgedByName() {
        return acknowledgedByName;
    }

    public String getResolvedByName() {
        return resolvedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
