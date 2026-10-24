package com.sentinel.modules.monitoring.alert;

import com.sentinel.modules.monitoring.entity.Incident;
import com.sentinel.modules.monitoring.entity.IncidentSeverity;
import com.sentinel.modules.project.entity.Project;

public interface AlertNotificationService {
    void sendAlert(Project project, Incident incident, String title, String message, IncidentSeverity severity);
}
