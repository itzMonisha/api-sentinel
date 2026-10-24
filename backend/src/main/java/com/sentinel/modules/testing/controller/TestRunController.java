package com.sentinel.modules.testing.controller;

import com.sentinel.modules.testing.dto.TestResultResponse;
import com.sentinel.modules.testing.dto.TestRunResponse;
import com.sentinel.modules.testing.service.TestExecutionService;
import com.sentinel.shared.model.ApiResponse;
import com.sentinel.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Test Runs & Results", description = "Test run inspection, granular step results, and endpoint test history")
public class TestRunController {

    private final TestExecutionService testExecutionService;

    public TestRunController(TestExecutionService testExecutionService) {
        this.testExecutionService = testExecutionService;
    }

    @GetMapping("/test-runs/{id}")
    @Operation(summary = "Get test run status, timings, and summary pass/fail counts")
    public ResponseEntity<ApiResponse<TestRunResponse>> getTestRun(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestRunResponse response = testExecutionService.getTestRun(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/test-runs/{id}/results")
    @Operation(summary = "Get detailed test results and assertion checks for each step in a test run")
    public ResponseEntity<ApiResponse<List<TestResultResponse>>> getTestRunResults(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TestResultResponse> response = testExecutionService.getTestRunResults(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/endpoints/{id}/test-runs")
    @Operation(summary = "Get test run execution history for a registered API endpoint")
    public ResponseEntity<ApiResponse<List<TestRunResponse>>> getEndpointTestRuns(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TestRunResponse> response = testExecutionService.getTestRunsByEndpoint(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
