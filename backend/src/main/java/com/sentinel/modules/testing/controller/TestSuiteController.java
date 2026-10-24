package com.sentinel.modules.testing.controller;

import com.sentinel.modules.testing.dto.CreateTestCaseRequest;
import com.sentinel.modules.testing.dto.CreateTestSuiteRequest;
import com.sentinel.modules.testing.dto.TestCaseResponse;
import com.sentinel.modules.testing.dto.TestRunResponse;
import com.sentinel.modules.testing.dto.TestSuiteResponse;
import com.sentinel.modules.testing.dto.UpdateTestSuiteRequest;
import com.sentinel.modules.testing.service.TestExecutionService;
import com.sentinel.modules.testing.service.TestSuiteService;
import com.sentinel.shared.model.ApiResponse;
import com.sentinel.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Test Suites & Execution", description = "Test suite creation, test cases, and test runs")
public class TestSuiteController {

    private final TestSuiteService testSuiteService;
    private final TestExecutionService testExecutionService;

    public TestSuiteController(TestSuiteService testSuiteService, TestExecutionService testExecutionService) {
        this.testSuiteService = testSuiteService;
        this.testExecutionService = testExecutionService;
    }

    @PostMapping("/test-suites")
    @Operation(summary = "Create a new test suite (project specified in body)")
    public ResponseEntity<ApiResponse<TestSuiteResponse>> createTestSuite(
            @Valid @RequestBody CreateTestSuiteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestSuiteResponse response = testSuiteService.createTestSuite(request.getProjectId(), request, principal);
        return new ResponseEntity<>(ApiResponse.created(response, "Test suite created successfully"), HttpStatus.CREATED);
    }

    @PostMapping("/projects/{projectId}/test-suites")
    @Operation(summary = "Create a new test suite under a project")
    public ResponseEntity<ApiResponse<TestSuiteResponse>> createTestSuiteForProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTestSuiteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestSuiteResponse response = testSuiteService.createTestSuite(projectId, request, principal);
        return new ResponseEntity<>(ApiResponse.created(response, "Test suite created successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/projects/{projectId}/test-suites")
    @Operation(summary = "List test suites for a project")
    public ResponseEntity<ApiResponse<List<TestSuiteResponse>>> getTestSuitesByProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TestSuiteResponse> response = testSuiteService.getTestSuitesByProject(projectId, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/test-suites/{id}")
    @Operation(summary = "Get test suite details and its test cases")
    public ResponseEntity<ApiResponse<TestSuiteResponse>> getTestSuite(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestSuiteResponse response = testSuiteService.getTestSuite(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/test-suites/{id}")
    @Operation(summary = "Update test suite metadata")
    public ResponseEntity<ApiResponse<TestSuiteResponse>> updateTestSuite(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestSuiteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestSuiteResponse response = testSuiteService.updateTestSuite(id, request, principal);
        return ResponseEntity.ok(ApiResponse.ok(response, "Test suite updated successfully"));
    }

    @DeleteMapping("/test-suites/{id}")
    @Operation(summary = "Delete a test suite")
    public ResponseEntity<ApiResponse<Void>> deleteTestSuite(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        testSuiteService.deleteTestSuite(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(null, "Test suite deleted successfully"));
    }

    @PostMapping("/test-suites/{id}/cases")
    @Operation(summary = "Add a test case with assertions to a test suite")
    public ResponseEntity<ApiResponse<TestCaseResponse>> addTestCase(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTestCaseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestCaseResponse response = testSuiteService.createTestCase(id, request, principal);
        return new ResponseEntity<>(ApiResponse.created(response, "Test case created successfully"), HttpStatus.CREATED);
    }

    @GetMapping("/test-suites/{id}/cases")
    @Operation(summary = "List all test cases in a test suite")
    public ResponseEntity<ApiResponse<List<TestCaseResponse>>> getTestCases(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TestCaseResponse> response = testSuiteService.getTestCasesBySuite(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/test-suites/{id}/runs")
    @Operation(summary = "Trigger execution of a test suite run")
    public ResponseEntity<ApiResponse<TestRunResponse>> triggerTestRun(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestRunResponse response = testExecutionService.triggerTestRun(id, principal);
        return new ResponseEntity<>(ApiResponse.created(response, "Test run executed"), HttpStatus.CREATED);
    }

    @GetMapping("/test-suites/{id}/runs")
    @Operation(summary = "Get historical test runs for a test suite")
    public ResponseEntity<ApiResponse<List<TestRunResponse>>> getTestRunsBySuite(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TestRunResponse> response = testExecutionService.getTestRunsBySuite(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
