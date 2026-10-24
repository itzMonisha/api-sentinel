package com.sentinel.modules.monitoring.dto;

import com.sentinel.modules.monitoring.entity.AlertChannel;
import com.sentinel.modules.monitoring.entity.AlertNotification;
import com.sentinel.modules.monitoring.entity.IncidentSeverity;

import java.time.Instant;
import java.util.UUID;

public class AlertNotificationResponse {

    private UUID id;
    private UUID projectId;
    private UUID incidentId;
    private String title;
    private String message;
    private AlertChannel channel;
    private IncidentSeverity severity;
    private boolean read;
    private Instant sentAt;

    public AlertNotificationResponse() {
    }

    public static AlertNotificationResponse fromEntity(AlertNotification alert) {
        AlertNotificationResponse res = new AlertNotificationResponse();
        res.id = alert.getId();
        res.projectId = alert.getProject() != null ? alert.getProject().getId() : null;
        res.incidentId = alert.getIncident() != null ? alert.getIncident().getId() : null;
        res.title = alert.getTitle();
        res.message = alert.getMessage();
        res.channel = alert.getChannel();
        res.severity = alert.getSeverity();
        res.read = alert.isRead();
        res.sentAt = alert.getSentAt();
        return res;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public AlertChannel getChannel() {
        return channel;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
