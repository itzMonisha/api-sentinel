package com.sentinel.modules.scanner.service;

import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.service.ProjectSecurityService;
import com.sentinel.modules.scanner.dto.CreateScanRequest;
import com.sentinel.modules.scanner.dto.SecurityFindingResponse;
import com.sentinel.modules.scanner.dto.SecurityScanResponse;
import com.sentinel.modules.scanner.entity.ScanStatus;
import com.sentinel.modules.scanner.entity.SecurityFinding;
import com.sentinel.modules.scanner.entity.SecurityScan;
import com.sentinel.modules.scanner.repository.SecurityFindingRepository;
import com.sentinel.modules.scanner.repository.SecurityScanRepository;
import com.sentinel.modules.scanner.rule.ScanContext;
import com.sentinel.modules.scanner.rule.ScannerCheck;
import com.sentinel.shared.exception.ApiException;
import com.sentinel.shared.exception.ResourceNotFoundException;
import com.sentinel.shared.net.SafeHttpClient;
import com.sentinel.shared.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SecurityScannerService {

    private static final Logger log = LoggerFactory.getLogger(SecurityScannerService.class);
    private static final int MAX_CONCURRENT_SCANS_PER_PROJECT = 1;
    private static final int MAX_CONCURRENT_SCANS_PER_USER = 5;

    private final SecurityScanRepository securityScanRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityService projectSecurityService;
    private final SafeHttpClient safeHttpClient;
    private final Sanitizer sanitizer;
    private final List<ScannerCheck> scannerChecks;

    public SecurityScannerService(
            SecurityScanRepository securityScanRepository,
            SecurityFindingRepository securityFindingRepository,
            ApiEndpointRepository apiEndpointRepository,
            UserRepository userRepository,
            ProjectSecurityService projectSecurityService,
            SafeHttpClient safeHttpClient,
            Sanitizer sanitizer,
            List<ScannerCheck> scannerChecks) {
        this.securityScanRepository = securityScanRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.apiEndpointRepository = apiEndpointRepository;
        this.userRepository = userRepository;
        this.projectSecurityService = projectSecurityService;
        this.safeHttpClient = safeHttpClient;
        this.sanitizer = sanitizer;
        this.scannerChecks = scannerChecks;
    }

    @Transactional
    public SecurityScanResponse createAndExecuteScan(CreateScanRequest request, UserPrincipal principal) {
        if (request.getProjectId() == null) {
            throw new ApiException("Project ID is required to launch a security scan", HttpStatus.BAD_REQUEST, "MISSING_PROJECT_ID");
        }

        Project project = projectSecurityService.getAuthorizedProject(request.getProjectId(), principal, ProjectRole.EDITOR);

        // Concurrency limit enforcement
        long runningInProject = securityScanRepository.countByProjectIdAndStatus(project.getId(), ScanStatus.RUNNING);
        if (runningInProject >= MAX_CONCURRENT_SCANS_PER_PROJECT) {
            throw new ApiException("A security scan is already currently running for this project",
                    HttpStatus.TOO_MANY_REQUESTS, "SCAN_CONCURRENCY_LIMIT_EXCEEDED");
        }

        if (principal != null) {
            long runningByUser = securityScanRepository.countByTriggeredByIdAndStatus(principal.getId(), ScanStatus.RUNNING);
            if (runningByUser >= MAX_CONCURRENT_SCANS_PER_USER) {
                throw new ApiException("User concurrent scan limit reached",
                        HttpStatus.TOO_MANY_REQUESTS, "USER_CONCURRENCY_LIMIT_EXCEEDED");
            }
        }

        // Determine targets: strictly restricted to registered endpoints within this authorized project
        List<ApiEndpoint> targets = new ArrayList<>();
        if (request.getEndpointIds() != null && !request.getEndpointIds().isEmpty()) {
            for (UUID endpointId : request.getEndpointIds()) {
                ApiEndpoint ep = apiEndpointRepository.findByIdAndDeletedAtIsNull(endpointId)
                        .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + endpointId));

                if (!ep.getProject().getId().equals(project.getId())) {
                    throw new ApiException("Target endpoint does not belong to authorized project: " + project.getName(),
                            HttpStatus.BAD_REQUEST, "TARGET_NOT_IN_PROJECT_ALLOWLIST");
                }
                targets.add(ep);
            }
        } else {
            targets = apiEndpointRepository.findAllByProjectIdAndDeletedAtIsNull(project.getId());
        }

        User triggeredBy = null;
        if (principal != null) {
            triggeredBy = userRepository.findById(principal.getId()).orElse(null);
        }

        // Initialize scan record
        SecurityScan scan = new SecurityScan(project, triggeredBy);
        scan = securityScanRepository.save(scan);

        scan.setStatus(ScanStatus.RUNNING);
        scan.setStartedAt(Instant.now());
        scan = securityScanRepository.save(scan);

        ScanContext context = new ScanContext(
                project,
                safeHttpClient,
                principal,
                request.getTestFixtureAuth(),
                sanitizer
        );

        log.info("Launching safe security scan {} against {} endpoints in project '{}' with {} modular checks",
                scan.getId(), targets.size(), project.getName(), scannerChecks.size());

        int checkCount = 0;
        for (ApiEndpoint target : targets) {
            for (ScannerCheck check : scannerChecks) {
                checkCount++;
                try {
                    List<SecurityFinding> findings = check.execute(target, context, scan);
                    if (findings != null && !findings.isEmpty()) {
                        for (SecurityFinding finding : findings) {
                            securityFindingRepository.save(finding);
                            scan.incrementSeverityCount(finding.getSeverity());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Check '{}' encountered error scanning endpoint '{}': {}",
                            check.getRuleId(), target.getName(), e.getMessage());
                }
            }
        }

        scan.setTotalChecks(checkCount);
        scan.setFinishedAt(Instant.now());
        scan.setStatus(ScanStatus.COMPLETED);

        SecurityScan completedScan = securityScanRepository.save(scan);
        return mapToScanResponse(completedScan);
    }

    @Transactional(readOnly = true)
    public SecurityScanResponse getScan(UUID scanId, UserPrincipal principal) {
        SecurityScan scan = securityScanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Security scan not found with id: " + scanId));

        projectSecurityService.getAuthorizedProject(scan.getProject().getId(), principal, ProjectRole.VIEWER);
        return mapToScanResponse(scan);
    }

    @Transactional(readOnly = true)
    public List<SecurityFindingResponse> getScanFindings(UUID scanId, UserPrincipal principal) {
        SecurityScan scan = securityScanRepository.findById(scanId)
                .orElseThrow(() -> new ResourceNotFoundException("Security scan not found with id: " + scanId));

        projectSecurityService.getAuthorizedProject(scan.getProject().getId(), principal, ProjectRole.VIEWER);

        List<SecurityFinding> findings = securityFindingRepository.findAllBySecurityScanIdOrderByCreatedAtAsc(scanId);
        List<SecurityFindingResponse> responses = new ArrayList<>();
        for (SecurityFinding f : findings) {
            responses.add(mapToFindingResponse(f));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<SecurityScanResponse> getScansByProject(UUID projectId, UserPrincipal principal) {
        projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);

        List<SecurityScan> scans = securityScanRepository.findAllByProjectIdOrderByCreatedAtDesc(projectId);
        List<SecurityScanResponse> responses = new ArrayList<>();
        for (SecurityScan s : scans) {
            responses.add(mapToScanResponse(s));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<SecurityFindingResponse> getFindingsByEndpoint(UUID endpointId, UserPrincipal principal) {
        ApiEndpoint endpoint = apiEndpointRepository.findByIdAndDeletedAtIsNull(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + endpointId));

        projectSecurityService.getAuthorizedProject(endpoint.getProject().getId(), principal, ProjectRole.VIEWER);

        List<SecurityFinding> findings = securityFindingRepository.findAllByEndpointIdOrderByCreatedAtDesc(endpointId);
        List<SecurityFindingResponse> responses = new ArrayList<>();
        for (SecurityFinding f : findings) {
            responses.add(mapToFindingResponse(f));
        }
        return responses;
    }

    // ==================== MAPPERS ====================

    private SecurityScanResponse mapToScanResponse(SecurityScan scan) {
        SecurityScanResponse resp = new SecurityScanResponse();
        resp.setId(scan.getId());
        resp.setProjectId(scan.getProject().getId());
        resp.setProjectName(scan.getProject().getName());
        if (scan.getTriggeredBy() != null) {
            resp.setTriggeredById(scan.getTriggeredBy().getId());
            resp.setTriggeredByEmail(scan.getTriggeredBy().getEmail());
        }
        resp.setStatus(scan.getStatus());
        resp.setTotalChecks(scan.getTotalChecks());
        resp.setCriticalCount(scan.getCriticalCount());
        resp.setHighCount(scan.getHighCount());
        resp.setMediumCount(scan.getMediumCount());
        resp.setLowCount(scan.getLowCount());
        resp.setInfoCount(scan.getInfoCount());
        resp.setStartedAt(scan.getStartedAt());
        resp.setFinishedAt(scan.getFinishedAt());
        resp.setCreatedAt(scan.getCreatedAt());
        return resp;
    }

    private SecurityFindingResponse mapToFindingResponse(SecurityFinding finding) {
        SecurityFindingResponse resp = new SecurityFindingResponse();
        resp.setId(finding.getId());
        resp.setSecurityScanId(finding.getSecurityScan().getId());
        if (finding.getEndpoint() != null) {
            resp.setEndpointId(finding.getEndpoint().getId());
            resp.setEndpointName(finding.getEndpoint().getName());
            resp.setEndpointUrl(finding.getEndpoint().getUrl());
        }
        resp.setRuleId(finding.getRuleId());
        resp.setTitle(finding.getTitle());
        resp.setSeverity(finding.getSeverity());
        resp.setConfidence(finding.getConfidence());
        resp.setOwaspCategory(finding.getOwaspCategory());
        resp.setDescription(finding.getDescription());
        resp.setEvidence(finding.getEvidence());
        resp.setRemediation(finding.getRemediation());
        resp.setCreatedAt(finding.getCreatedAt());
        return resp;
    }
}
