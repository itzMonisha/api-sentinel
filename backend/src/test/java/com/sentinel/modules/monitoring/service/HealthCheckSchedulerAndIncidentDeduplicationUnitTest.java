package com.sentinel.modules.monitoring.service;

import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.monitoring.alert.AlertNotificationService;
import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.monitoring.entity.HealthCheckRecord;
import com.sentinel.modules.monitoring.entity.HealthCheckStatus;
import com.sentinel.modules.monitoring.entity.Incident;
import com.sentinel.modules.monitoring.entity.IncidentSeverity;
import com.sentinel.modules.monitoring.entity.IncidentStatus;
import com.sentinel.modules.monitoring.repository.AlertNotificationRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRecordRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRepository;
import com.sentinel.modules.monitoring.repository.IncidentRepository;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.service.ProjectSecurityService;
import com.sentinel.shared.net.SafeHttpClient;
import com.sentinel.shared.net.TargetValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthCheckSchedulerAndIncidentDeduplicationUnitTest {

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
    private SafeHttpClient safeHttpClient;

    @Mock
    private TargetValidator targetValidator;

    @Mock
    private AlertNotificationService alertNotificationService;

    private ReliabilityMonitoringService monitoringService;
    private HealthCheckScheduler scheduler;

    private Project testProject;
    private ApiEndpoint testEndpoint;
    private HealthCheck testHealthCheck;

    @BeforeEach
    void setUp() {
        monitoringService = new ReliabilityMonitoringService(
                healthCheckRepository,
                recordRepository,
                incidentRepository,
                alertRepository,
                projectSecurityService,
                endpointRepository,
                null,
                safeHttpClient,
                targetValidator,
                alertNotificationService
        );

        scheduler = new HealthCheckScheduler(
                healthCheckRepository,
                monitoringService,
                true,
                4
        );

        User owner = new User("owner@apisentinel.dev", "hash", "Owner", Role.ROLE_USER);
        testProject = new Project("Billing API", "billing-api", "Payment system", owner);
        testEndpoint = new ApiEndpoint(testProject, "Charge Endpoint", "https://api.example.com/charges", "POST");
        testHealthCheck = new HealthCheck(testProject, testEndpoint, "Charge Monitor", 60, 5000, 200, 3, 2);

        when(recordRepository.save(any(HealthCheckRecord.class))).thenAnswer(invocation -> {
            HealthCheckRecord r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    @Test
    @DisplayName("Incident Deduplication: Ongoing outage updates existing active incident without creating duplicate records")
    void shouldDeduplicateIncidentOnSubsequentFailures() {
        // Arrange: health check already at failure threshold (3 failures)
        testHealthCheck.setConsecutiveFailures(2);

        // Active incident exists
        Incident existingIncident = new Incident(
                testProject,
                testEndpoint,
                testHealthCheck,
                "Outage: Charge Monitor",
                "Outage description",
                IncidentSeverity.MAJOR
        );
        existingIncident.setFailureCount(3);
        existingIncident.setStatus(IncidentStatus.TRIGGERED);

        when(incidentRepository.findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(
                eq(testHealthCheck),
                eq(List.of(IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED))
        )).thenReturn(Optional.of(existingIncident));

        // Act: Execute probe that fails with HTTP 500
        when(safeHttpClient.execute(any())).thenReturn(
                new com.sentinel.shared.net.HttpResponseSnapshot(500, java.util.Map.of(), "Internal Server Error", 250L, false, null, false)
        );

        monitoringService.executeProbe(testHealthCheck);

        // Assert: Existing incident count is incremented, and no second incident is created
        assertThat(existingIncident.getFailureCount()).isEqualTo(4);
        verify(incidentRepository).save(existingIncident);
        verify(alertNotificationService, never()).sendAlert(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Auto-Recovery: When consecutive successes reach recovery threshold, incident resolves automatically")
    void shouldAutoResolveIncidentWhenRecoveryThresholdReached() {
        // Arrange: 1 success already, recovery threshold is 2
        testHealthCheck.setConsecutiveSuccesses(1);
        testHealthCheck.setRecoveryThreshold(2);

        Incident openIncident = new Incident(
                testProject,
                testEndpoint,
                testHealthCheck,
                "Outage: Charge Monitor",
                "Down",
                IncidentSeverity.MAJOR
        );
        openIncident.setStatus(IncidentStatus.TRIGGERED);

        when(incidentRepository.findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(
                eq(testHealthCheck),
                eq(List.of(IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED))
        )).thenReturn(Optional.of(openIncident));

        when(safeHttpClient.execute(any())).thenReturn(
                new com.sentinel.shared.net.HttpResponseSnapshot(200, java.util.Map.of(), "OK", 120L, false, null, true)
        );

        // Act: Probe succeeds, bringing consecutive successes to 2
        monitoringService.executeProbe(testHealthCheck);

        // Assert: Incident is marked RESOLVED and recovery notification is dispatched
        assertThat(openIncident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(openIncident.getResolvedAt()).isNotNull();
        assertThat(openIncident.getRootCause()).contains("automatically recovered");

        verify(incidentRepository).save(openIncident);
        verify(alertNotificationService).sendAlert(
                eq(testProject),
                eq(openIncident),
                contains("Endpoint Recovered"),
                anyString(),
                eq(IncidentSeverity.MINOR)
        );
    }

    @Test
    @DisplayName("Scheduler Poll Cycle: Finds due health checks, defers nextCheckAt, and dispatches to worker pool")
    void shouldScheduleAndExecuteDueChecks() throws InterruptedException {
        // Arrange: health check is due
        testHealthCheck.setNextCheckAt(Instant.now().minusSeconds(10));
        when(healthCheckRepository.findDueChecks(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(testHealthCheck));

        when(healthCheckRepository.findById(testHealthCheck.getId())).thenReturn(Optional.of(testHealthCheck));
        when(safeHttpClient.execute(any())).thenReturn(
                new com.sentinel.shared.net.HttpResponseSnapshot(200, java.util.Map.of(), "OK", 100L, false, null, true)
        );

        // Act: Execute poll cycle
        scheduler.pollAndExecuteDueHealthChecks();

        // Give background worker pool thread a brief moment to run
        Thread.sleep(200);

        // Assert: nextCheckAt was advanced forward by intervalSeconds
        assertThat(testHealthCheck.getNextCheckAt()).isAfter(Instant.now());
        verify(healthCheckRepository, atLeastOnce()).save(testHealthCheck);
    }
}
