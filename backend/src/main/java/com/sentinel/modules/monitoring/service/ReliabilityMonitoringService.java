package com.sentinel.modules.monitoring.service;

import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.monitoring.alert.AlertNotificationService;
import com.sentinel.modules.monitoring.dto.*;
import com.sentinel.modules.monitoring.entity.*;
import com.sentinel.modules.monitoring.repository.AlertNotificationRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRecordRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRepository;
import com.sentinel.modules.monitoring.repository.IncidentRepository;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.service.ProjectSecurityService;
import com.sentinel.shared.exception.ResourceNotFoundException;
import com.sentinel.shared.exception.ValidationException;
import com.sentinel.shared.net.HttpRequestSnapshot;
import com.sentinel.shared.net.HttpResponseSnapshot;
import com.sentinel.shared.net.SafeHttpClient;
import com.sentinel.shared.net.TargetValidator;
import com.sentinel.shared.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReliabilityMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(ReliabilityMonitoringService.class);
    private static final int MAX_HEALTH_CHECKS_PER_PROJECT = 50;

    private final HealthCheckRepository healthCheckRepository;
    private final HealthCheckRecordRepository recordRepository;
    private final IncidentRepository incidentRepository;
    private final AlertNotificationRepository alertRepository;
    private final ProjectSecurityService projectSecurityService;
    private final ApiEndpointRepository endpointRepository;
    private final UserRepository userRepository;
    private final SafeHttpClient safeHttpClient;
    private final TargetValidator targetValidator;
    private final AlertNotificationService alertNotificationService;

    public ReliabilityMonitoringService(
            HealthCheckRepository healthCheckRepository,
            HealthCheckRecordRepository recordRepository,
            IncidentRepository incidentRepository,
            AlertNotificationRepository alertRepository,
            ProjectSecurityService projectSecurityService,
            ApiEndpointRepository endpointRepository,
            UserRepository userRepository,
            SafeHttpClient safeHttpClient,
            TargetValidator targetValidator,
            AlertNotificationService alertNotificationService) {
        this.healthCheckRepository = healthCheckRepository;
        this.recordRepository = recordRepository;
        this.incidentRepository = incidentRepository;
        this.alertRepository = alertRepository;
        this.projectSecurityService = projectSecurityService;
        this.endpointRepository = endpointRepository;
        this.userRepository = userRepository;
        this.safeHttpClient = safeHttpClient;
        this.targetValidator = targetValidator;
        this.alertNotificationService = alertNotificationService;
    }

    // ==========================================
    // Health Check Configuration & CRUD
    // ==========================================

    @Transactional
    public HealthCheckResponse createHealthCheck(UUID projectId, CreateHealthCheckRequest request, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.EDITOR);

        long count = healthCheckRepository.countByProject(project);
        if (count >= MAX_HEALTH_CHECKS_PER_PROJECT) {
            throw new ValidationException("Project health-check quota exceeded. Maximum allowed: " + MAX_HEALTH_CHECKS_PER_PROJECT);
        }

        ApiEndpoint endpoint = endpointRepository.findById(request.getEndpointId())
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + request.getEndpointId()));

        if (!endpoint.getProject().getId().equals(project.getId())) {
            throw new ValidationException("Endpoint does not belong to the specified project");
        }

        targetValidator.validateAndResolve(endpoint.getUrl());

        String name = request.getName() != null && !request.getName().isBlank()
                ? request.getName()
                : "Health Probe: " + endpoint.getName();

        HealthCheck healthCheck = new HealthCheck(
                project,
                endpoint,
                name,
                request.getIntervalSeconds() != null ? request.getIntervalSeconds() : 60,
                request.getTimeoutMs() != null ? request.getTimeoutMs() : 5000,
                request.getExpectedStatus() != null ? request.getExpectedStatus() : 200,
                request.getFailureThreshold() != null ? request.getFailureThreshold() : 3,
                request.getRecoveryThreshold() != null ? request.getRecoveryThreshold() : 2
        );

        if (request.getActive() != null) {
            healthCheck.setActive(request.getActive());
        }

        healthCheck = healthCheckRepository.save(healthCheck);
        log.info("Configured health check '{}' for endpoint '{}' (interval: {}s, threshold: {})",
                healthCheck.getName(), endpoint.getUrl(), healthCheck.getIntervalSeconds(), healthCheck.getFailureThreshold());

        return HealthCheckResponse.fromEntity(healthCheck, calculateMetrics(healthCheck));
    }

    @Transactional
    public HealthCheckResponse updateHealthCheck(UUID checkId, UpdateHealthCheckRequest request, UserPrincipal principal) {
        HealthCheck healthCheck = healthCheckRepository.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Health check not found with id: " + checkId));

        projectSecurityService.getAuthorizedProject(healthCheck.getProject().getId(), principal, ProjectRole.EDITOR);

        if (request.getName() != null && !request.getName().isBlank()) {
            healthCheck.setName(request.getName());
        }
        if (request.getIntervalSeconds() != null) {
            healthCheck.setIntervalSeconds(request.getIntervalSeconds());
        }
        if (request.getTimeoutMs() != null) {
            healthCheck.setTimeoutMs(request.getTimeoutMs());
        }
        if (request.getExpectedStatus() != null) {
            healthCheck.setExpectedStatus(request.getExpectedStatus());
        }
        if (request.getFailureThreshold() != null) {
            healthCheck.setFailureThreshold(request.getFailureThreshold());
        }
        if (request.getRecoveryThreshold() != null) {
            healthCheck.setRecoveryThreshold(request.getRecoveryThreshold());
        }
        if (request.getActive() != null) {
            healthCheck.setActive(request.getActive());
        }

        healthCheck = healthCheckRepository.save(healthCheck);
        return HealthCheckResponse.fromEntity(healthCheck, calculateMetrics(healthCheck));
    }

    @Transactional(readOnly = true)
    public HealthCheckResponse getHealthCheck(UUID checkId, UserPrincipal principal) {
        HealthCheck healthCheck = healthCheckRepository.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Health check not found with id: " + checkId));
        projectSecurityService.getAuthorizedProject(healthCheck.getProject().getId(), principal, ProjectRole.VIEWER);
        return HealthCheckResponse.fromEntity(healthCheck, calculateMetrics(healthCheck));
    }

    @Transactional(readOnly = true)
    public List<HealthCheckResponse> getHealthChecksByProject(UUID projectId, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);
        List<HealthCheck> checks = healthCheckRepository.findByProject(project);
        return checks.stream()
                .map(check -> HealthCheckResponse.fromEntity(check, calculateMetrics(check)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<HealthCheckRecordResponse> getHealthCheckRecords(UUID checkId, int limit, UserPrincipal principal) {
        HealthCheck healthCheck = healthCheckRepository.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Health check not found with id: " + checkId));
        projectSecurityService.getAuthorizedProject(healthCheck.getProject().getId(), principal, ProjectRole.VIEWER);

        int max = Math.min(Math.max(limit, 1), 100);
        List<HealthCheckRecord> records = recordRepository.findByHealthCheckOrderByExecutedAtDesc(healthCheck, PageRequest.of(0, max));
        return records.stream().map(HealthCheckRecordResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public void deleteHealthCheck(UUID checkId, UserPrincipal principal) {
        HealthCheck healthCheck = healthCheckRepository.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Health check not found with id: " + checkId));
        projectSecurityService.getAuthorizedProject(healthCheck.getProject().getId(), principal, ProjectRole.EDITOR);
        healthCheckRepository.delete(healthCheck);
        log.info("Deleted health check '{}' (id: {})", healthCheck.getName(), checkId);
    }

    // ==========================================
    // Probe Execution & Incident State Engine
    // ==========================================

    @Transactional
    public HealthCheckRecordResponse triggerHealthCheck(UUID checkId, UserPrincipal principal) {
        HealthCheck healthCheck = healthCheckRepository.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Health check not found with id: " + checkId));
        projectSecurityService.getAuthorizedProject(healthCheck.getProject().getId(), principal, ProjectRole.EDITOR);
        return executeProbe(healthCheck);
    }

    @Transactional
    public HealthCheckRecordResponse executeProbeById(UUID checkId) {
        HealthCheck healthCheck = healthCheckRepository.findById(checkId)
                .orElseThrow(() -> new ResourceNotFoundException("Health check not found with id: " + checkId));
        return executeProbe(healthCheck);
    }

    @Transactional
    public HealthCheckRecordResponse executeProbe(HealthCheck healthCheck) {
        ApiEndpoint endpoint = healthCheck.getEndpoint();
        Project project = healthCheck.getProject();

        HttpRequestSnapshot request = HttpRequestSnapshot.builder()
                .url(endpoint.getUrl())
                .httpMethod("GET")
                .headers(Map.of("Accept", "application/json", "User-Agent", "APISentinel-HealthProbe/1.0"))
                .readTimeoutMs(healthCheck.getTimeoutMs())
                .connectTimeoutMs(Math.min(healthCheck.getTimeoutMs(), 3000))
                .build();

        HttpResponseSnapshot response = safeHttpClient.execute(request);

        boolean probeSuccess = response.isSuccess() && response.getStatusCode() == healthCheck.getExpectedStatus();
        String errorMessage = probeSuccess ? null : (response.getErrorMessage() != null ? response.getErrorMessage() : "Unexpected HTTP Status: " + response.getStatusCode());

        HealthCheckRecord record = new HealthCheckRecord(
                healthCheck,
                endpoint,
                response.getStatusCode(),
                response.getResponseTimeMs(),
                probeSuccess,
                errorMessage
        );
        record = recordRepository.save(record);

        healthCheck.setLastCheckedAt(Instant.now());
        healthCheck.setNextCheckAt(Instant.now().plusSeconds(healthCheck.getIntervalSeconds()));

        if (probeSuccess) {
            handleProbeSuccess(healthCheck, project, endpoint);
        } else {
            handleProbeFailure(healthCheck, project, endpoint, response.getStatusCode(), errorMessage);
        }

        healthCheckRepository.save(healthCheck);
        return HealthCheckRecordResponse.fromEntity(record);
    }

    private void handleProbeSuccess(HealthCheck healthCheck, Project project, ApiEndpoint endpoint) {
        healthCheck.setConsecutiveSuccesses(healthCheck.getConsecutiveSuccesses() + 1);
        healthCheck.setConsecutiveFailures(0);
        healthCheck.setLastStatus(HealthCheckStatus.HEALTHY);

        // Check if recovery threshold crossed and active incident exists
        if (healthCheck.getConsecutiveSuccesses() >= healthCheck.getRecoveryThreshold()) {
            Optional<Incident> openIncidentOpt = incidentRepository.findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(
                    healthCheck,
                    List.of(IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED)
            );

            if (openIncidentOpt.isPresent()) {
                Incident incident = openIncidentOpt.get();
                incident.setStatus(IncidentStatus.RESOLVED);
                incident.setResolvedAt(Instant.now());
                incident.setRootCause("Endpoint automatically recovered with " + healthCheck.getConsecutiveSuccesses() + " consecutive successful probes.");
                incidentRepository.save(incident);

                log.info("[INCIDENT-RECOVERED] Incident {} for '{}' auto-resolved after {} successful probes",
                        incident.getId(), endpoint.getName(), healthCheck.getConsecutiveSuccesses());

                alertNotificationService.sendAlert(
                        project,
                        incident,
                        "Endpoint Recovered: " + endpoint.getName(),
                        "Health check recovered with " + healthCheck.getConsecutiveSuccesses() + " consecutive successful probes.",
                        IncidentSeverity.MINOR
                );
            }
        }
    }

    private void handleProbeFailure(HealthCheck healthCheck, Project project, ApiEndpoint endpoint, Integer statusCode, String errorMessage) {
        int failures = healthCheck.getConsecutiveFailures() + 1;
        healthCheck.setConsecutiveFailures(failures);
        healthCheck.setConsecutiveSuccesses(0);

        if (failures >= healthCheck.getFailureThreshold()) {
            healthCheck.setLastStatus(HealthCheckStatus.DOWN);

            // Deduplication: Check if there is already an active incident for this health check
            Optional<Incident> openIncidentOpt = incidentRepository.findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(
                    healthCheck,
                    List.of(IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED)
            );

            if (openIncidentOpt.isPresent()) {
                // Deduplicate: increment count and update existing incident without creating duplicates
                Incident incident = openIncidentOpt.get();
                incident.setFailureCount(incident.getFailureCount() + 1);
                incidentRepository.save(incident);
                log.debug("[INCIDENT-DEDUPLICATED] Ongoing outage on '{}', total probe failures: {}", endpoint.getName(), incident.getFailureCount());
            } else {
                // Threshold crossed: Create new incident
                IncidentSeverity severity = failures >= 5 ? IncidentSeverity.CRITICAL : IncidentSeverity.MAJOR;
                String desc = String.format("Endpoint failed %d consecutive health checks. Last error: %s (Status: %s)",
                        failures, errorMessage != null ? errorMessage : "Unknown error", statusCode != null ? statusCode : "N/A");

                Incident incident = new Incident(
                        project,
                        endpoint,
                        healthCheck,
                        "Outage: " + endpoint.getName() + " (" + endpoint.getUrl() + ")",
                        desc,
                        severity
                );
                incident.setFailureCount(failures);
                incident = incidentRepository.save(incident);

                log.warn("[INCIDENT-TRIGGERED] Created incident {} for '{}' after {} consecutive failures",
                        incident.getId(), endpoint.getName(), failures);

                alertNotificationService.sendAlert(
                        project,
                        incident,
                        "Outage Alert: " + endpoint.getName() + " is DOWN",
                        desc,
                        severity
                );
            }
        } else {
            healthCheck.setLastStatus(HealthCheckStatus.DEGRADED);
        }
    }

    // ==========================================
    // Incident Management
    // ==========================================

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidentsByProject(UUID projectId, IncidentStatus status, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);
        List<Incident> incidents = status != null
                ? incidentRepository.findByProjectAndStatusOrderByStartedAtDesc(project, status, PageRequest.of(0, 100))
                : incidentRepository.findByProjectOrderByStartedAtDesc(project, PageRequest.of(0, 100));

        return incidents.stream().map(IncidentResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID incidentId, UserPrincipal principal) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));
        projectSecurityService.getAuthorizedProject(incident.getProject().getId(), principal, ProjectRole.VIEWER);
        return IncidentResponse.fromEntity(incident);
    }

    @Transactional
    public IncidentResponse acknowledgeIncident(UUID incidentId, UserPrincipal principal) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));
        projectSecurityService.getAuthorizedProject(incident.getProject().getId(), principal, ProjectRole.EDITOR);

        User currentUser = userRepository.findById(principal.getId()).orElse(null);

        if (incident.getStatus() == IncidentStatus.TRIGGERED) {
            incident.setStatus(IncidentStatus.ACKNOWLEDGED);
            incident.setAcknowledgedAt(Instant.now());
            incident.setAcknowledgedBy(currentUser);
            incident = incidentRepository.save(incident);
            log.info("Incident {} acknowledged by {}", incidentId, principal.getUsername());
        }

        return IncidentResponse.fromEntity(incident);
    }

    @Transactional
    public IncidentResponse resolveIncident(UUID incidentId, ResolveIncidentRequest request, UserPrincipal principal) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));
        projectSecurityService.getAuthorizedProject(incident.getProject().getId(), principal, ProjectRole.EDITOR);

        User currentUser = userRepository.findById(principal.getId()).orElse(null);

        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now());
        incident.setResolvedBy(currentUser);
        if (request != null && request.getRootCause() != null && !request.getRootCause().isBlank()) {
            incident.setRootCause(request.getRootCause());
        } else if (incident.getRootCause() == null) {
            incident.setRootCause("Manually resolved by operator: " + principal.getUsername());
        }

        incident = incidentRepository.save(incident);
        log.info("Incident {} resolved by {}", incidentId, principal.getUsername());
        return IncidentResponse.fromEntity(incident);
    }

    // ==========================================
    // Alerts & Notifications
    // ==========================================

    @Transactional(readOnly = true)
    public List<AlertNotificationResponse> getAlertsByProject(UUID projectId, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);
        List<AlertNotification> alerts = alertRepository.findByProjectOrderBySentAtDesc(project, PageRequest.of(0, 50));
        return alerts.stream().map(AlertNotificationResponse::fromEntity).collect(Collectors.toList());
    }

    @Transactional
    public void markAlertRead(UUID alertId, UserPrincipal principal) {
        AlertNotification alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));
        projectSecurityService.getAuthorizedProject(alert.getProject().getId(), principal, ProjectRole.VIEWER);
        alert.setRead(true);
        alertRepository.save(alert);
    }

    // ==========================================
    // Dashboard Telemetry
    // ==========================================

    @Transactional(readOnly = true)
    public ProjectReliabilityDashboardResponse getProjectDashboard(UUID projectId, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);
        List<HealthCheck> checks = healthCheckRepository.findByProject(project);

        int total = checks.size();
        int healthy = 0;
        int degraded = 0;
        int down = 0;
        List<Double> uptimes = new ArrayList<>();

        for (HealthCheck check : checks) {
            if (check.getLastStatus() == HealthCheckStatus.HEALTHY) healthy++;
            else if (check.getLastStatus() == HealthCheckStatus.DEGRADED) degraded++;
            else if (check.getLastStatus() == HealthCheckStatus.DOWN) down++;

            HealthMetricsDto m = calculateMetrics(check);
            if (m.isHasSufficientData() && m.getUptimePercentage() != null) {
                uptimes.add(m.getUptimePercentage());
            }
        }

        long activeIncidents = incidentRepository.countByProjectAndStatus(project, IncidentStatus.TRIGGERED)
                + incidentRepository.countByProjectAndStatus(project, IncidentStatus.ACKNOWLEDGED);
        long totalIncidents = incidentRepository.countByProject(project);
        long unreadAlerts = alertRepository.countByProjectAndReadFalse(project);

        Double avgUptime = null;
        if (!uptimes.isEmpty()) {
            double sum = uptimes.stream().mapToDouble(Double::doubleValue).sum();
            avgUptime = Math.round((sum / uptimes.size()) * 100.0) / 100.0;
        }

        ProjectReliabilityDashboardResponse res = new ProjectReliabilityDashboardResponse();
        res.setProjectId(project.getId());
        res.setProjectName(project.getName());
        res.setTotalMonitoredEndpoints(total);
        res.setHealthyEndpoints(healthy);
        res.setDegradedEndpoints(degraded);
        res.setDownEndpoints(down);
        res.setOverallUptimePercentage(avgUptime);
        res.setActiveIncidentsCount((int) activeIncidents);
        res.setTotalIncidents((int) totalIncidents);
        res.setUnreadAlertsCount((int) unreadAlerts);
        return res;
    }

    // ==========================================
    // Metric Calculation Helper
    // ==========================================

    public HealthMetricsDto calculateMetrics(HealthCheck healthCheck) {
        List<HealthCheckRecord> recentRecords = recordRepository.findRecentRecords(healthCheck, PageRequest.of(0, 100));

        long total = recentRecords.size();
        long successes = recentRecords.stream().filter(HealthCheckRecord::isSuccess).count();
        long failures = total - successes;

        List<Long> latencies = recentRecords.stream()
                .map(HealthCheckRecord::getResponseTimeMs)
                .collect(Collectors.toList());

        return HealthMetricsDto.calculate(latencies, total, successes, failures, healthCheck.getLastStatus());
    }
}
