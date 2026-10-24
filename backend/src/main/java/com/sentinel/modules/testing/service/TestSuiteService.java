package com.sentinel.modules.testing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.service.ProjectSecurityService;
import com.sentinel.modules.testing.dto.AssertionDefinition;
import com.sentinel.modules.testing.dto.CreateTestCaseRequest;
import com.sentinel.modules.testing.dto.CreateTestSuiteRequest;
import com.sentinel.modules.testing.dto.TestCaseResponse;
import com.sentinel.modules.testing.dto.TestSuiteResponse;
import com.sentinel.modules.testing.dto.UpdateTestCaseRequest;
import com.sentinel.modules.testing.dto.UpdateTestSuiteRequest;
import com.sentinel.modules.testing.entity.TestCase;
import com.sentinel.modules.testing.entity.TestSuite;
import com.sentinel.modules.testing.repository.TestCaseRepository;
import com.sentinel.modules.testing.repository.TestSuiteRepository;
import com.sentinel.shared.exception.ApiException;
import com.sentinel.shared.exception.ResourceNotFoundException;
import com.sentinel.shared.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TestSuiteService {

    private final TestSuiteRepository testSuiteRepository;
    private final TestCaseRepository testCaseRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ProjectSecurityService projectSecurityService;
    private final ObjectMapper objectMapper;

    public TestSuiteService(
            TestSuiteRepository testSuiteRepository,
            TestCaseRepository testCaseRepository,
            ApiEndpointRepository apiEndpointRepository,
            ProjectSecurityService projectSecurityService,
            ObjectMapper objectMapper) {
        this.testSuiteRepository = testSuiteRepository;
        this.testCaseRepository = testCaseRepository;
        this.apiEndpointRepository = apiEndpointRepository;
        this.projectSecurityService = projectSecurityService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TestSuiteResponse createTestSuite(UUID projectId, CreateTestSuiteRequest request, UserPrincipal principal) {
        UUID effectiveProjectId = projectId != null ? projectId : request.getProjectId();
        if (effectiveProjectId == null) {
            throw new ApiException("Project ID is required to create a test suite", HttpStatus.BAD_REQUEST, "MISSING_PROJECT_ID");
        }

        Project project = projectSecurityService.getAuthorizedProject(effectiveProjectId, principal, ProjectRole.EDITOR);

        TestSuite suite = new TestSuite(project, request.getName().trim(), request.getDescription());
        TestSuite saved = testSuiteRepository.save(suite);

        return mapToSuiteResponse(saved, Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public TestSuiteResponse getTestSuite(UUID suiteId, UserPrincipal principal) {
        TestSuite suite = testSuiteRepository.findByIdAndDeletedAtIsNull(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Test suite not found with id: " + suiteId));

        projectSecurityService.getAuthorizedProject(suite.getProject().getId(), principal, ProjectRole.VIEWER);

        List<TestCase> cases = testCaseRepository.findAllByTestSuiteIdAndDeletedAtIsNullOrderByExecutionOrderAsc(suiteId);
        return mapToSuiteResponse(suite, cases);
    }

    @Transactional(readOnly = true)
    public List<TestSuiteResponse> getTestSuitesByProject(UUID projectId, UserPrincipal principal) {
        projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);

        List<TestSuite> suites = testSuiteRepository.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId);
        List<TestSuiteResponse> responses = new ArrayList<>();
        for (TestSuite suite : suites) {
            List<TestCase> cases = testCaseRepository.findAllByTestSuiteIdAndDeletedAtIsNullOrderByExecutionOrderAsc(suite.getId());
            responses.add(mapToSuiteResponse(suite, cases));
        }
        return responses;
    }

    @Transactional
    public TestSuiteResponse updateTestSuite(UUID suiteId, UpdateTestSuiteRequest request, UserPrincipal principal) {
        TestSuite suite = testSuiteRepository.findByIdAndDeletedAtIsNull(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Test suite not found with id: " + suiteId));

        projectSecurityService.getAuthorizedProject(suite.getProject().getId(), principal, ProjectRole.EDITOR);

        if (request.getName() != null && !request.getName().isBlank()) {
            suite.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            suite.setDescription(request.getDescription());
        }

        TestSuite updated = testSuiteRepository.save(suite);
        List<TestCase> cases = testCaseRepository.findAllByTestSuiteIdAndDeletedAtIsNullOrderByExecutionOrderAsc(suiteId);
        return mapToSuiteResponse(updated, cases);
    }

    @Transactional
    public void deleteTestSuite(UUID suiteId, UserPrincipal principal) {
        TestSuite suite = testSuiteRepository.findByIdAndDeletedAtIsNull(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Test suite not found with id: " + suiteId));

        projectSecurityService.getAuthorizedProject(suite.getProject().getId(), principal, ProjectRole.EDITOR);

        suite.markDeleted();
        testSuiteRepository.save(suite);
    }

    // ==================== TEST CASE CRUD ====================

    @Transactional
    public TestCaseResponse createTestCase(UUID suiteId, CreateTestCaseRequest request, UserPrincipal principal) {
        TestSuite suite = testSuiteRepository.findByIdAndDeletedAtIsNull(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Test suite not found with id: " + suiteId));

        Project project = projectSecurityService.getAuthorizedProject(suite.getProject().getId(), principal, ProjectRole.EDITOR);

        ApiEndpoint endpoint = null;
        if (request.getEndpointId() != null) {
            endpoint = apiEndpointRepository.findByIdAndDeletedAtIsNull(request.getEndpointId())
                    .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + request.getEndpointId()));

            if (!endpoint.getProject().getId().equals(project.getId())) {
                throw new ApiException("Endpoint does not belong to project: " + project.getName(),
                        HttpStatus.BAD_REQUEST, "CROSS_PROJECT_RESOURCE_VIOLATION");
            }
        }

        TestCase testCase = new TestCase();
        testCase.setTestSuite(suite);
        testCase.setEndpoint(endpoint);
        testCase.setName(request.getName().trim());
        testCase.setExecutionOrder(request.getExecutionOrder());
        testCase.setHttpMethod(request.getHttpMethod() != null ? request.getHttpMethod().toUpperCase() : "GET");
        testCase.setTargetPath(request.getTargetPath().trim());
        testCase.setRequestBody(request.getRequestBody());

        testCase.setHeaders(writeJson(request.getHeaders()));
        testCase.setQueryParams(writeJson(request.getQueryParams()));
        testCase.setAssertions(writeJson(request.getAssertions()));

        TestCase saved = testCaseRepository.save(testCase);
        return mapToCaseResponse(saved);
    }

    @Transactional(readOnly = true)
    public TestCaseResponse getTestCase(UUID caseId, UserPrincipal principal) {
        TestCase testCase = testCaseRepository.findByIdAndDeletedAtIsNull(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case not found with id: " + caseId));

        projectSecurityService.getAuthorizedProject(testCase.getTestSuite().getProject().getId(), principal, ProjectRole.VIEWER);
        return mapToCaseResponse(testCase);
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> getTestCasesBySuite(UUID suiteId, UserPrincipal principal) {
        TestSuite suite = testSuiteRepository.findByIdAndDeletedAtIsNull(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Test suite not found with id: " + suiteId));

        projectSecurityService.getAuthorizedProject(suite.getProject().getId(), principal, ProjectRole.VIEWER);

        List<TestCase> cases = testCaseRepository.findAllByTestSuiteIdAndDeletedAtIsNullOrderByExecutionOrderAsc(suiteId);
        List<TestCaseResponse> responses = new ArrayList<>();
        for (TestCase c : cases) {
            responses.add(mapToCaseResponse(c));
        }
        return responses;
    }

    @Transactional
    public TestCaseResponse updateTestCase(UUID caseId, UpdateTestCaseRequest request, UserPrincipal principal) {
        TestCase testCase = testCaseRepository.findByIdAndDeletedAtIsNull(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case not found with id: " + caseId));

        Project project = projectSecurityService.getAuthorizedProject(testCase.getTestSuite().getProject().getId(), principal, ProjectRole.EDITOR);

        if (request.getName() != null && !request.getName().isBlank()) {
            testCase.setName(request.getName().trim());
        }
        if (request.getExecutionOrder() != null) {
            testCase.setExecutionOrder(request.getExecutionOrder());
        }
        if (request.getHttpMethod() != null) {
            testCase.setHttpMethod(request.getHttpMethod().toUpperCase());
        }
        if (request.getTargetPath() != null && !request.getTargetPath().isBlank()) {
            testCase.setTargetPath(request.getTargetPath().trim());
        }
        if (request.getRequestBody() != null) {
            testCase.setRequestBody(request.getRequestBody());
        }
        if (request.getHeaders() != null) {
            testCase.setHeaders(writeJson(request.getHeaders()));
        }
        if (request.getQueryParams() != null) {
            testCase.setQueryParams(writeJson(request.getQueryParams()));
        }
        if (request.getAssertions() != null) {
            testCase.setAssertions(writeJson(request.getAssertions()));
        }
        if (request.getEndpointId() != null) {
            ApiEndpoint endpoint = apiEndpointRepository.findByIdAndDeletedAtIsNull(request.getEndpointId())
                    .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + request.getEndpointId()));

            if (!endpoint.getProject().getId().equals(project.getId())) {
                throw new ApiException("Endpoint does not belong to project: " + project.getName(),
                        HttpStatus.BAD_REQUEST, "CROSS_PROJECT_RESOURCE_VIOLATION");
            }
            testCase.setEndpoint(endpoint);
        }

        TestCase updated = testCaseRepository.save(testCase);
        return mapToCaseResponse(updated);
    }

    @Transactional
    public void deleteTestCase(UUID caseId, UserPrincipal principal) {
        TestCase testCase = testCaseRepository.findByIdAndDeletedAtIsNull(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case not found with id: " + caseId));

        projectSecurityService.getAuthorizedProject(testCase.getTestSuite().getProject().getId(), principal, ProjectRole.EDITOR);

        testCase.markDeleted();
        testCaseRepository.save(testCase);
    }

    // ==================== MAPPERS ====================

    private TestSuiteResponse mapToSuiteResponse(TestSuite suite, List<TestCase> cases) {
        TestSuiteResponse resp = new TestSuiteResponse();
        resp.setId(suite.getId());
        resp.setProjectId(suite.getProject().getId());
        resp.setName(suite.getName());
        resp.setDescription(suite.getDescription());
        resp.setCreatedAt(suite.getCreatedAt());
        resp.setUpdatedAt(suite.getUpdatedAt());

        List<TestCaseResponse> caseResponses = new ArrayList<>();
        if (cases != null) {
            for (TestCase c : cases) {
                caseResponses.add(mapToCaseResponse(c));
            }
        }
        resp.setTestCases(caseResponses);
        resp.setCaseCount(caseResponses.size());
        return resp;
    }

    public TestCaseResponse mapToCaseResponse(TestCase testCase) {
        TestCaseResponse resp = new TestCaseResponse();
        resp.setId(testCase.getId());
        resp.setTestSuiteId(testCase.getTestSuite().getId());
        if (testCase.getEndpoint() != null) {
            resp.setEndpointId(testCase.getEndpoint().getId());
            resp.setEndpointName(testCase.getEndpoint().getName());
        }
        resp.setName(testCase.getName());
        resp.setExecutionOrder(testCase.getExecutionOrder());
        resp.setHttpMethod(testCase.getHttpMethod());
        resp.setTargetPath(testCase.getTargetPath());
        resp.setRequestBody(testCase.getRequestBody());
        resp.setHeaders(readHeaders(testCase.getHeaders()));
        resp.setQueryParams(readHeaders(testCase.getQueryParams()));
        resp.setAssertions(readAssertions(testCase.getAssertions()));
        resp.setCreatedAt(testCase.getCreatedAt());
        resp.setUpdatedAt(testCase.getUpdatedAt());
        return resp;
    }

    private String writeJson(Object obj) {
        if (obj == null) return "[]";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    public Map<String, String> readHeaders(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public List<AssertionDefinition> readAssertions(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AssertionDefinition>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
