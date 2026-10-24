package com.sentinel.modules.monitoring.alert;

import com.sentinel.modules.monitoring.entity.AlertChannel;
import com.sentinel.modules.monitoring.entity.AlertNotification;
import com.sentinel.modules.monitoring.entity.Incident;
import com.sentinel.modules.monitoring.entity.IncidentSeverity;
import com.sentinel.modules.monitoring.repository.AlertNotificationRepository;
import com.sentinel.modules.project.entity.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAlertNotificationService implements AlertNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAlertNotificationService.class);

    private final AlertNotificationRepository alertRepository;

    @Value("${api-sentinel.alert.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${api-sentinel.alert.email.to:admin@apisentinel.dev}")
    private String emailTo;

    public DefaultAlertNotificationService(AlertNotificationRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    @Override
    @Transactional
    public void sendAlert(Project project, Incident incident, String title, String message, IncidentSeverity severity) {
        log.warn("[ALERT-NOTIFICATION] [{}] Project: '{}' - {}: {}", severity, project.getName(), title, message);

        // 1. In-App Notification Delivery
        AlertNotification inAppAlert = new AlertNotification(
                project,
                incident,
                title,
                message,
                AlertChannel.IN_APP,
                severity
        );
        alertRepository.save(inAppAlert);

        // 2. Safe development / optional email provider integration
        if (emailEnabled) {
            deliverEmailAlert(project, incident, title, message, severity);
        }
    }

    protected void deliverEmailAlert(Project project, Incident incident, String title, String message, IncidentSeverity severity) {
        try {
            log.info("[ALERT-EMAIL-MOCK] Safe dispatch of alert email to {}: Subject='[{}] {}'", emailTo, severity, title);
            // Simulated / Safe mail transport hook without failing the main monitoring transaction
        } catch (Exception e) {
            log.error("Failed to dispatch alert email notification: {}", e.getMessage());
        }
    }
}
