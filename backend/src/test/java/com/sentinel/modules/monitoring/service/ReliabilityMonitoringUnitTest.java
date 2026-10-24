package com.sentinel.modules.monitoring.service;

import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.monitoring.alert.AlertNotificationService;
import com.sentinel.modules.monitoring.dto.HealthMetricsDto;
import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.monitoring.entity.HealthCheckStatus;
import com.sentinel.modules.monitoring.entity.Incident;
import com.sentinel.modules.monitoring.entity.IncidentStatus;
import com.sentinel.modules.monitoring.repository.AlertNotificationRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRecordRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRepository;
import com.sentinel.modules.monitoring.repository.IncidentRepository;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.service.ProjectSecurityService;
import com.sentinel.shared.net.HttpRequestSnapshot;
import com.sentinel.shared.net.HttpResponseSnapshot;
import com.sentinel.shared.net.SafeHttpClient;
import com.sentinel.shared.net.TargetValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReliabilityMonitoringUnitTest {

    @Mock
    private HealthCheckRepository healthCheckRepository;

    @Mock
    private HealthCheckRecordRepository recordRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private AlertNotificationRepository alertRepository;

    @Mock
    private ProjectSecurityService projectSecurityService;

    @Mock
    private ApiEndpointRepository endpointRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SafeHttpClient safeHttpClient;

    @Mock
    private TargetValidator targetValidator;

    @Mock
    private AlertNotificationService alertNotificationService;

    private ReliabilityMonitoringService service;

    private Project testProject;
    private ApiEndpoint testEndpoint;
    private HealthCheck testHealthCheck;

    @BeforeEach
    void setUp() {
        service = new ReliabilityMonitoringService(
                healthCheckRepository,
                recordRepository,
                incidentRepository,
                alertRepository,
                projectSecurityService,
                endpointRepository,
                userRepository,
                safeHttpClient,
                targetValidator,
                alertNotificationService
        );

        User owner = new User("owner@apisentinel.dev", "password", "Project Owner", Role.ROLE_USER);
        testProject = new Project("Reliability Test Workspace", "rel-workspace", "Workspace", owner);
        testEndpoint = new ApiEndpoint(testProject, "Payments API", "https://api.example.com/payments", "GET");
        testHealthCheck = new HealthCheck(testProject, testEndpoint, "Payments Probe", 60, 5000, 200, 3, 2);

        lenient().when(recordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(incidentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(healthCheckRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ==========================================
    // 1. Metric Windows & Formula Verification
    // ==========================================

    @Test
    @DisplayName("Metrics: Safely handles zero denominator when no persisted checks exist")
    void shouldHandleZeroDenominatorWithoutErrors() {
        HealthMetricsDto metrics = HealthMetricsDto.calculate(Collections.emptyList(), 0, 0, 0, HealthCheckStatus.UNKNOWN);

        assertThat(metrics.getTotalChecks()).isEqualTo(0);
        assertThat(metrics.getFailureRate()).isEqualTo(0.0);
        assertThat(metrics.getUptimePercentage()).isNull();
        assertThat(metrics.isHasSufficientData()).isFalse();
        assertThat(metrics.getAvgLatencyMs()).isEqualTo(0.0);
        assertThat(metrics.getP50LatencyMs()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Metrics: Marks insufficient data when checks are below minimum threshold (3 checks)")
    void shouldMarkInsufficientDataForFewChecks() {
        List<Long> latencies = List.of(120L, 150L);
        HealthMetricsDto metrics = HealthMetricsDto.calculate(latencies, 2, 2, 0, HealthCheckStatus.HEALTHY);

        assertThat(metrics.getTotalChecks()).isEqualTo(2);
        assertThat(metrics.isHasSufficientData()).isFalse();
        assertThat(metrics.getUptimePercentage()).isNull(); // Does not claim historical uptime without sufficient data
        assertThat(metrics.getFailureRate()).isEqualTo(0.0);
        assertThat(metrics.getAvgLatencyMs()).isEqualTo(135.0);
    }

    @Test
    @DisplayName("Metrics: Accurately calculates uptime, failure rate, and percentiles with sufficient data")
    void shouldCalculateAccurateMetricsAndPercentiles() {
        List<Long> latencies = List.of(50L, 100L, 150L, 200L, 300L);
        HealthMetricsDto metrics = HealthMetricsDto.calculate(latencies, 5, 4, 1, HealthCheckStatus.HEALTHY);

        assertThat(metrics.getTotalChecks()).isEqualTo(5);
        assertThat(metrics.isHasSufficientData()).isTrue();
        assertThat(metrics.getUptimePercentage()).isEqualTo(80.0);
        assertThat(metrics.getFailureRate()).isEqualTo(0.2);
        assertThat(metrics.getP50LatencyMs()).isEqualTo(150L);
        assertThat(metrics.getP95LatencyMs()).isEqualTo(300L);
        assertThat(metrics.getP99LatencyMs()).isEqualTo(300L);
        assertThat(metrics.getAvgLatencyMs()).isEqualTo(160.0);
    }

    // ==========================================
    // 2. Incident Threshold Crossing
    // ==========================================

    @Test
    @DisplayName("Incident Creation: Degrades status on 1st/2nd failure and triggers Incident on 3rd consecutive failure")
    void shouldCreateIncidentWhenThresholdCrossed() {
        when(safeHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(
                new HttpResponseSnapshot(503, Map.of(), "Service Unavailable", 120, false, "Service Unavailable", false)
        );
        when(incidentRepository.findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        // Probe 1: 1 failure -> DEGRADED, no incident
        service.executeProbe(testHealthCheck);
        assertThat(testHealthCheck.getConsecutiveFailures()).isEqualTo(1);
        assertThat(testHealthCheck.getLastStatus()).isEqualTo(HealthCheckStatus.DEGRADED);
        verify(incidentRepository, never()).save(any(Incident.class));

        // Probe 2: 2 failures -> DEGRADED, no incident
        service.executeProbe(testHealthCheck);
        assertThat(testHealthCheck.getConsecutiveFailures()).isEqualTo(2);
        assertThat(testHealthCheck.getLastStatus()).isEqualTo(HealthCheckStatus.DEGRADED);
        verify(incidentRepository, never()).save(any(Incident.class));

        // Probe 3: 3 failures (threshold reached) -> DOWN, incident triggered!
        service.executeProbe(testHealthCheck);
        assertThat(testHealthCheck.getConsecutiveFailures()).isEqualTo(3);
        assertThat(testHealthCheck.getLastStatus()).isEqualTo(HealthCheckStatus.DOWN);
        verify(incidentRepository, times(1)).save(any(Incident.class));
        verify(alertNotificationService, times(1)).sendAlert(any(), any(), any(), any(), any());
    }

    // ==========================================
    // 3. Incident Deduplication
    // ==========================================

    @Test
    @DisplayName("Deduplication: Ongoing outage updates existing incident instead of creating duplicate records")
    void shouldDeduplicateIncidentsDuringOngoingOutage() {
        when(safeHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(
                new HttpResponseSnapshot(500, Map.of(), "Internal Server Error", 80, false, "Server Error", false)
        );

        testHealthCheck.setConsecutiveFailures(3); // Already at threshold

        Incident existingIncident = new Incident(testProject, testEndpoint, testHealthCheck, "Existing Incident", "Outage", null);
        existingIncident.setFailureCount(3);
        when(incidentRepository.findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.of(existingIncident));

        // 4th failure occurs
        service.executeProbe(testHealthCheck);

        assertThat(testHealthCheck.getConsecutiveFailures()).isEqualTo(4);
        assertThat(existingIncident.getFailureCount()).isEqualTo(4);

        // Verify save was called to update the existing incident, NOT to create a new one
        verify(incidentRepository, times(1)).save(existingIncident);
        // Alert is NOT spammed on every incremental failure during ongoing outage
        verify(alertNotificationService, never()).sendAlert(any(), any(), any(), any(), any());
    }

    // ==========================================
    // 4. Incident Auto-Recovery
    // ==========================================

    @Test
    @DisplayName("Auto-Recovery: Consecutive successes resolve active incident when recovery threshold (2) is met")
    void shouldAutoResolveIncidentUponRecoveryThreshold() {
        when(safeHttpClient.execute(any(HttpRequestSnapshot.class))).thenReturn(
                new HttpResponseSnapshot(200, Map.of(), "{\"status\":\"ok\"}", 45, false, null, true)
        );

        testHealthCheck.setConsecutiveFailures(4);
        testHealthCheck.setLastStatus(HealthCheckStatus.DOWN);

        Incident activeIncident = new Incident(testProject, testEndpoint, testHealthCheck, "Active Incident", "Outage", null);
        activeIncident.setStatus(IncidentStatus.TRIGGERED);
        when(incidentRepository.findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(any(), any()))
                .thenReturn(Optional.of(activeIncident));

        // Success 1 (recoveryThreshold = 2): consecutiveSuccesses = 1, incident remains TRIGGERED
        service.executeProbe(testHealthCheck);
        assertThat(testHealthCheck.getConsecutiveSuccesses()).isEqualTo(1);
        assertThat(testHealthCheck.getConsecutiveFailures()).isEqualTo(0);
        assertThat(activeIncident.getStatus()).isEqualTo(IncidentStatus.TRIGGERED);

        // Success 2 (recoveryThreshold met): incident auto-resolves to RESOLVED!
        service.executeProbe(testHealthCheck);
        assertThat(testHealthCheck.getConsecutiveSuccesses()).isEqualTo(2);
        assertThat(activeIncident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(activeIncident.getResolvedAt()).isNotNull();
        assertThat(activeIncident.getRootCause()).contains("automatically recovered");

        verify(incidentRepository, times(1)).save(activeIncident);
        verify(alertNotificationService, times(1)).sendAlert(any(), any(), any(), any(), any());
    }
}
