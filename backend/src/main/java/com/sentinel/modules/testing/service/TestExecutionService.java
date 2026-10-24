package com.sentinel.modules.testing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.service.ProjectSecurityService;
import com.sentinel.modules.testing.assertion.AssertionEngine;
import com.sentinel.modules.testing.dto.AssertionDefinition;
import com.sentinel.modules.testing.dto.AssertionResult;
import com.sentinel.modules.testing.dto.TestResultResponse;
import com.sentinel.modules.testing.dto.TestRunResponse;
import com.sentinel.modules.testing.entity.TestCase;
import com.sentinel.modules.testing.entity.TestResult;
import com.sentinel.modules.testing.entity.TestRun;
import com.sentinel.modules.testing.entity.TestStatus;
import com.sentinel.modules.testing.entity.TestSuite;
import com.sentinel.modules.testing.repository.TestCaseRepository;
import com.sentinel.modules.testing.repository.TestResultRepository;
import com.sentinel.modules.testing.repository.TestRunRepository;
import com.sentinel.modules.testing.repository.TestSuiteRepository;
import com.sentinel.shared.exception.ApiException;
import com.sentinel.shared.exception.ResourceNotFoundException;
import com.sentinel.shared.net.HttpRequestSnapshot;
import com.sentinel.shared.net.HttpResponseSnapshot;
import com.sentinel.shared.net.SafeHttpClient;
import com.sentinel.shared.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TestExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TestExecutionService.class);
    private static final int MAX_CONCURRENT_RUNS_PER_PROJECT = 5;
    private static final int MAX_CONCURRENT_RUNS_PER_USER = 5;

    private final TestSuiteRepository testSuiteRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestRunRepository testRunRepository;
    private final TestResultRepository testResultRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final UserRepository userRepository;
    private final ProjectSecurityService projectSecurityService;
    private final SafeHttpClient safeHttpClient;
    private final AssertionEngine assertionEngine;
    private final ObjectMapper objectMapper;

    public TestExecutionService(
            TestSuiteRepository testSuiteRepository,
            TestCaseRepository testCaseRepository,
            TestRunRepository testRunRepository,
            TestResultRepository testResultRepository,
            ApiEndpointRepository apiEndpointRepository,
            UserRepository userRepository,
            ProjectSecurityService projectSecurityService,
            SafeHttpClient safeHttpClient,
            AssertionEngine assertionEngine,
            ObjectMapper objectMapper) {
        this.testSuiteRepository = testSuiteRepository;
        this.testCaseRepository = testCaseRepository;
        this.testRunRepository = testRunRepository;
        this.testResultRepository = testResultRepository;
        this.apiEndpointRepository = apiEndpointRepository;
        this.userRepository = userRepository;
        this.projectSecurityService = projectSecurityService;
        this.safeHttpClient = safeHttpClient;
        this.assertionEngine = assertionEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Triggers and executes a test suite run, managing its complete lifecycle and persisting step results.
     */
    @Transactional
    public TestRunResponse triggerTestRun(UUID suiteId, UserPrincipal principal) {
        TestSuite suite = testSuiteRepository.findByIdAndDeletedAtIsNull(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Test suite not found with id: " + suiteId));

        UUID projectId = suite.getProject().getId();
        projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.EDITOR);

        // Enforce concurrency limits per project and per user
        long runningInProject = testRunRepository.countByTestSuiteProjectIdAndStatus(projectId, TestStatus.RUNNING);
        if (runningInProject >= MAX_CONCURRENT_RUNS_PER_PROJECT) {
            throw new ApiException("Maximum concurrent test runs (" + MAX_CONCURRENT_RUNS_PER_PROJECT + ") exceeded for project",
                    HttpStatus.TOO_MANY_REQUESTS, "PROJECT_CONCURRENCY_LIMIT_EXCEEDED");
        }

        if (principal != null) {
            long runningByUser = testRunRepository.countByTriggeredByIdAndStatus(principal.getId(), TestStatus.RUNNING);
            if (runningByUser >= MAX_CONCURRENT_RUNS_PER_USER) {
                throw new ApiException("Maximum concurrent test runs (" + MAX_CONCURRENT_RUNS_PER_USER + ") exceeded for user",
                        HttpStatus.TOO_MANY_REQUESTS, "USER_CONCURRENCY_LIMIT_EXCEEDED");
            }
        }

        User triggeredBy = null;
        if (principal != null) {
            triggeredBy = userRepository.findById(principal.getId()).orElse(null);
        }

        // Initialize TestRun in QUEUED state
        TestRun run = new TestRun(suite, triggeredBy);
        run = testRunRepository.save(run);

        // Transition to RUNNING
        run.setStatus(TestStatus.RUNNING);
        Instant startedAt = Instant.now();
        run.setStartedAt(startedAt);
        run = testRunRepository.save(run);

        // Fetch ordered test cases
        List<TestCase> testCases = testCaseRepository.findAllByTestSuiteIdAndDeletedAtIsNullOrderByExecutionOrderAsc(suiteId);
        int total = testCases.size();
        int passed = 0;
        int failed = 0;

        for (TestCase tc : testCases) {
            TestResult result = executeTestCase(run, tc);
            testResultRepository.save(result);

            if (result.getStatus() == TestStatus.PASSED) {
                passed++;
            } else {
                failed++;
            }
        }

        // Finalize TestRun status and duration
        Instant finishedAt = Instant.now();
        long durationMs = Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli());

        run.setTotalTests(total);
        run.setPassedTests(passed);
        run.setFailedTests(failed);
        run.setDurationMs(durationMs);
        run.setFinishedAt(finishedAt);

        if (total == 0) {
            run.setStatus(TestStatus.PASSED);
        } else if (failed > 0) {
            run.setStatus(TestStatus.FAILED);
        } else {
            run.setStatus(TestStatus.PASSED);
        }

        TestRun finishedRun = testRunRepository.save(run);
        return mapToRunResponse(finishedRun);
    }

    private TestResult executeTestCase(TestRun run, TestCase tc) {
        String targetUrl = resolveTargetUrl(tc);
        Map<String, String> headers = parseMap(tc.getHeaders());
        Map<String, String> queryParams = parseMap(tc.getQueryParams());
        List<AssertionDefinition> assertions = parseAssertions(tc.getAssertions());

        HttpRequestSnapshot requestSnapshot = HttpRequestSnapshot.builder()
                .httpMethod(tc.getHttpMethod())
                .url(targetUrl)
                .headers(headers)
                .queryParams(queryParams)
                .body(tc.getRequestBody())
                .build();

        log.info("Executing test case '{}' [{} {}]", tc.getName(), tc.getHttpMethod(), targetUrl);

        // Execute via SafeHttpClient
        HttpResponseSnapshot responseSnapshot = safeHttpClient.execute(requestSnapshot);

        String endpointSchema = (tc.getEndpoint() != null) ? tc.getEndpoint().getResponseSchema() : null;

        // Evaluate assertions via AssertionEngine
        List<AssertionResult> assertionResults = assertionEngine.evaluate(assertions, responseSnapshot, endpointSchema);

        boolean allAssertionsPassed = assertionResults.stream().allMatch(AssertionResult::isPassed);

        TestStatus status;
        if (!responseSnapshot.isSuccess() && responseSnapshot.getStatusCode() == 0) {
            status = TestStatus.ERROR;
        } else if (allAssertionsPassed) {
            status = TestStatus.PASSED;
        } else {
            status = TestStatus.FAILED;
        }

        String serializedAssertions = writeJson(assertionResults);
        String errorMessage = responseSnapshot.getErrorMessage();
        if (errorMessage == null && !allAssertionsPassed) {
            errorMessage = assertionResults.stream()
                    .filter(a -> !a.isPassed())
                    .map(AssertionResult::getMessage)
                    .findFirst()
                    .orElse("One or more assertions failed");
        }

        Integer statusCode = responseSnapshot.getStatusCode() > 0 ? responseSnapshot.getStatusCode() : null;

        return new TestResult(
                run,
                tc,
                tc.getName(),
                status,
                statusCode,
                responseSnapshot.getResponseTimeMs(),
                serializedAssertions,
                errorMessage
        );
    }

    private String resolveTargetUrl(TestCase tc) {
        String path = tc.getTargetPath();
        if (path != null && (path.startsWith("http://") || path.startsWith("https://"))) {
            return path;
        }

        ApiEndpoint endpoint = tc.getEndpoint();
        if (endpoint != null && endpoint.getUrl() != null && !endpoint.getUrl().isBlank()) {
            String baseUrl = endpoint.getUrl().trim();
            if (path == null || path.isBlank() || path.equals("/")) {
                return baseUrl;
            }
            if (baseUrl.endsWith(path)) {
                return baseUrl;
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return baseUrl + path;
        }

        return path != null ? path : "";
    }

    @Transactional(readOnly = true)
    public TestRunResponse getTestRun(UUID runId, UserPrincipal principal) {
        TestRun run = testRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Test run not found with id: " + runId));

        projectSecurityService.getAuthorizedProject(run.getTestSuite().getProject().getId(), principal, ProjectRole.VIEWER);
        return mapToRunResponse(run);
    }

    @Transactional(readOnly = true)
    public List<TestResultResponse> getTestRunResults(UUID runId, UserPrincipal principal) {
        TestRun run = testRunRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Test run not found with id: " + runId));

        projectSecurityService.getAuthorizedProject(run.getTestSuite().getProject().getId(), principal, ProjectRole.VIEWER);

        List<TestResult> results = testResultRepository.findAllByTestRunIdOrderByCreatedAtAsc(runId);
        List<TestResultResponse> responses = new ArrayList<>();
        for (TestResult r : results) {
            responses.add(mapToResultResponse(r));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<TestRunResponse> getTestRunsBySuite(UUID suiteId, UserPrincipal principal) {
        TestSuite suite = testSuiteRepository.findByIdAndDeletedAtIsNull(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Test suite not found with id: " + suiteId));

        projectSecurityService.getAuthorizedProject(suite.getProject().getId(), principal, ProjectRole.VIEWER);

        List<TestRun> runs = testRunRepository.findAllByTestSuiteIdOrderByCreatedAtDesc(suiteId);
        List<TestRunResponse> responses = new ArrayList<>();
        for (TestRun r : runs) {
            responses.add(mapToRunResponse(r));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public List<TestRunResponse> getTestRunsByEndpoint(UUID endpointId, UserPrincipal principal) {
        ApiEndpoint endpoint = apiEndpointRepository.findByIdAndDeletedAtIsNull(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + endpointId));

        projectSecurityService.getAuthorizedProject(endpoint.getProject().getId(), principal, ProjectRole.VIEWER);

        List<TestRun> runs = testRunRepository.findDistinctTestRunsByEndpointId(endpointId);
        List<TestRunResponse> responses = new ArrayList<>();
        for (TestRun r : runs) {
            responses.add(mapToRunResponse(r));
        }
        return responses;
    }

    // ==================== MAPPERS & UTILITIES ====================

    private TestRunResponse mapToRunResponse(TestRun run) {
        TestRunResponse resp = new TestRunResponse();
        resp.setId(run.getId());
        resp.setTestSuiteId(run.getTestSuite().getId());
        resp.setTestSuiteName(run.getTestSuite().getName());
        if (run.getTriggeredBy() != null) {
            resp.setTriggeredById(run.getTriggeredBy().getId());
            resp.setTriggeredByEmail(run.getTriggeredBy().getEmail());
        }
        resp.setStatus(run.getStatus());
        resp.setTotalTests(run.getTotalTests());
        resp.setPassedTests(run.getPassedTests());
        resp.setFailedTests(run.getFailedTests());
        resp.setDurationMs(run.getDurationMs());
        resp.setStartedAt(run.getStartedAt());
        resp.setFinishedAt(run.getFinishedAt());
        resp.setCreatedAt(run.getCreatedAt());
        return resp;
    }

    private TestResultResponse mapToResultResponse(TestResult result) {
        TestResultResponse resp = new TestResultResponse();
        resp.setId(result.getId());
        resp.setTestRunId(result.getTestRun().getId());
        if (result.getTestCase() != null) {
            resp.setTestCaseId(result.getTestCase().getId());
        }
        resp.setTestCaseName(result.getTestCaseName());
        resp.setStatus(result.getStatus());
        resp.setHttpStatusCode(result.getHttpStatusCode());
        resp.setResponseTimeMs(result.getResponseTimeMs());
        resp.setAssertionResults(readAssertionResults(result.getAssertionResults()));
        resp.setErrorMessage(result.getErrorMessage());
        resp.setCreatedAt(result.getCreatedAt());
        return resp;
    }

    private Map<String, String> parseMap(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private List<AssertionDefinition> parseAssertions(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AssertionDefinition>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<AssertionResult> readAssertionResults(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AssertionResult>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String writeJson(Object obj) {
        if (obj == null) return "[]";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
