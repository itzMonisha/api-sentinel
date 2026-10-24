package com.sentinel.modules.scanner.controller;

import com.sentinel.modules.scanner.dto.CreateScanRequest;
import com.sentinel.modules.scanner.dto.SecurityFindingResponse;
import com.sentinel.modules.scanner.dto.SecurityScanResponse;
import com.sentinel.modules.scanner.service.SecurityScannerService;
import com.sentinel.shared.model.ApiResponse;
import com.sentinel.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Security Scanner", description = "Safe automated vulnerability scanning and security findings")
public class SecurityScanController {

    private final SecurityScannerService scannerService;

    public SecurityScanController(SecurityScannerService scannerService) {
        this.scannerService = scannerService;
    }

    @PostMapping("/scans")
    @Operation(summary = "Launch a safe security scan against project endpoints (projectId in body)")
    public ResponseEntity<ApiResponse<SecurityScanResponse>> launchScan(
            @RequestBody CreateScanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SecurityScanResponse response = scannerService.createAndExecuteScan(request, principal);
        return new ResponseEntity<>(ApiResponse.created(response, "Security scan completed successfully"), HttpStatus.CREATED);
    }

    @PostMapping("/projects/{projectId}/scans")
    @Operation(summary = "Launch a safe security scan for a project")
    public ResponseEntity<ApiResponse<SecurityScanResponse>> launchProjectScan(
            @PathVariable UUID projectId,
            @RequestBody(required = false) CreateScanRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CreateScanRequest effectiveRequest = request != null ? request : new CreateScanRequest();
        effectiveRequest.setProjectId(projectId);
        SecurityScanResponse response = scannerService.createAndExecuteScan(effectiveRequest, principal);
        return new ResponseEntity<>(ApiResponse.created(response, "Security scan completed successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/scans/{id}")
    @Operation(summary = "Get security scan progress, status, and severity breakdown")
    public ResponseEntity<ApiResponse<SecurityScanResponse>> getScan(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        SecurityScanResponse response = scannerService.getScan(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/scans/{id}/findings")
    @Operation(summary = "List findings discovered in a security scan with evidence and remediation")
    public ResponseEntity<ApiResponse<List<SecurityFindingResponse>>> getScanFindings(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SecurityFindingResponse> response = scannerService.getScanFindings(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/projects/{projectId}/scans")
    @Operation(summary = "List security scan history for a project")
    public ResponseEntity<ApiResponse<List<SecurityScanResponse>>> getProjectScans(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SecurityScanResponse> response = scannerService.getScansByProject(projectId, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/endpoints/{id}/security-findings")
    @Operation(summary = "List historical security findings for a registered endpoint")
    public ResponseEntity<ApiResponse<List<SecurityFindingResponse>>> getEndpointFindings(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SecurityFindingResponse> response = scannerService.getFindingsByEndpoint(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
