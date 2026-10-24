package com.sentinel.modules.monitoring.controller;

import com.sentinel.modules.monitoring.dto.*;
import com.sentinel.modules.monitoring.entity.IncidentStatus;
import com.sentinel.modules.monitoring.service.ReliabilityMonitoringService;
import com.sentinel.shared.model.ApiResponse;
import com.sentinel.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reliability Monitoring & Incidents", description = "Periodic endpoint health probes, incident state machine, and SLA tracking")
public class ReliabilityMonitoringController {

    private final ReliabilityMonitoringService monitoringService;

    public ReliabilityMonitoringController(ReliabilityMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    // ==========================================
    // Health Checks
    // ==========================================

    @PostMapping("/projects/{projectId}/health-checks")
    @Operation(summary = "Configure periodic health check schedule for an endpoint")
    public ResponseEntity<ApiResponse<HealthCheckResponse>> createHealthCheck(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateHealthCheckRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        HealthCheckResponse res = monitoringService.createHealthCheck(projectId, request, principal);
        return new ResponseEntity<>(ApiResponse.created(res, "Health check configured successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/projects/{projectId}/health-checks")
    @Operation(summary = "List all configured health check probes for a project")
    public ResponseEntity<ApiResponse<List<HealthCheckResponse>>> getProjectHealthChecks(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<HealthCheckResponse> list = monitoringService.getHealthChecksByProject(projectId, principal);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/health-checks/{id}")
    @Operation(summary = "Get health check configuration and real-time reliability telemetry")
    public ResponseEntity<ApiResponse<HealthCheckResponse>> getHealthCheck(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        HealthCheckResponse res = monitoringService.getHealthCheck(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    @PutMapping("/health-checks/{id}")
    @Operation(summary = "Update health check configuration (interval, thresholds, expected status)")
    public ResponseEntity<ApiResponse<HealthCheckResponse>> updateHealthCheck(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateHealthCheckRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        HealthCheckResponse res = monitoringService.updateHealthCheck(id, request, principal);
        return ResponseEntity.ok(ApiResponse.ok(res, "Health check updated successfully"));
    }

    @DeleteMapping("/health-checks/{id}")
    @Operation(summary = "Remove health check configuration")
    public ResponseEntity<ApiResponse<Void>> deleteHealthCheck(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        monitoringService.deleteHealthCheck(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(null, "Health check deleted successfully"));
    }

    @PostMapping("/health-checks/{id}/trigger")
    @Operation(summary = "Execute an immediate on-demand health probe")
    public ResponseEntity<ApiResponse<HealthCheckRecordResponse>> triggerProbe(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        HealthCheckRecordResponse res = monitoringService.triggerHealthCheck(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(res, "Health probe executed successfully"));
    }

    @GetMapping("/health-checks/{id}/history")
    @Operation(summary = "Get historical health check records and latency measurements")
    public ResponseEntity<ApiResponse<List<HealthCheckRecordResponse>>> getHealthCheckHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<HealthCheckRecordResponse> list = monitoringService.getHealthCheckRecords(id, limit, principal);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    // ==========================================
    // Incident Management
    // ==========================================

    @GetMapping("/projects/{projectId}/incidents")
    @Operation(summary = "List incident records for a project, optionally filtered by status")
    public ResponseEntity<ApiResponse<List<IncidentResponse>>> getProjectIncidents(
            @PathVariable UUID projectId,
            @RequestParam(required = false) IncidentStatus status,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<IncidentResponse> list = monitoringService.getIncidentsByProject(projectId, status, principal);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Get detailed incident report, failure timeline, and root cause")
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncident(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        IncidentResponse res = monitoringService.getIncident(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }

    @PostMapping("/incidents/{id}/acknowledge")
    @Operation(summary = "Acknowledge an active incident")
    public ResponseEntity<ApiResponse<IncidentResponse>> acknowledgeIncident(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        IncidentResponse res = monitoringService.acknowledgeIncident(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(res, "Incident acknowledged successfully"));
    }

    @PostMapping("/incidents/{id}/resolve")
    @Operation(summary = "Resolve an incident with a root cause summary")
    public ResponseEntity<ApiResponse<IncidentResponse>> resolveIncident(
            @PathVariable UUID id,
            @RequestBody(required = false) ResolveIncidentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        IncidentResponse res = monitoringService.resolveIncident(id, request, principal);
        return ResponseEntity.ok(ApiResponse.ok(res, "Incident resolved successfully"));
    }

    // ==========================================
    // In-App Alert Center
    // ==========================================

    @GetMapping("/projects/{projectId}/alerts")
    @Operation(summary = "Get in-app alert notifications for a project")
    public ResponseEntity<ApiResponse<List<AlertNotificationResponse>>> getProjectAlerts(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<AlertNotificationResponse> list = monitoringService.getAlertsByProject(projectId, principal);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/alerts/{id}/read")
    @Operation(summary = "Mark an alert notification as read")
    public ResponseEntity<ApiResponse<Void>> markAlertRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        monitoringService.markAlertRead(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(null, "Alert marked as read"));
    }

    // ==========================================
    // Dashboard Telemetry
    // ==========================================

    @GetMapping("/projects/{projectId}/dashboard/summary")
    @Operation(summary = "Get real-time aggregated reliability metrics and incident summary")
    public ResponseEntity<ApiResponse<ProjectReliabilityDashboardResponse>> getProjectDashboard(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ProjectReliabilityDashboardResponse res = monitoringService.getProjectDashboard(projectId, principal);
        return ResponseEntity.ok(ApiResponse.ok(res));
    }
}
