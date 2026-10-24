package com.sentinel.modules.testing.controller;

import com.sentinel.modules.testing.dto.TestCaseResponse;
import com.sentinel.modules.testing.dto.UpdateTestCaseRequest;
import com.sentinel.modules.testing.service.TestSuiteService;
import com.sentinel.shared.model.ApiResponse;
import com.sentinel.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test-cases")
@Tag(name = "Test Cases", description = "Test case definition, inspection, and lifecycle")
public class TestCaseController {

    private final TestSuiteService testSuiteService;

    public TestCaseController(TestSuiteService testSuiteService) {
        this.testSuiteService = testSuiteService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a test case definition and its assertions")
    public ResponseEntity<ApiResponse<TestCaseResponse>> getTestCase(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestCaseResponse response = testSuiteService.getTestCase(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a test case definition")
    public ResponseEntity<ApiResponse<TestCaseResponse>> updateTestCase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTestCaseRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestCaseResponse response = testSuiteService.updateTestCase(id, request, principal);
        return ResponseEntity.ok(ApiResponse.ok(response, "Test case updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a test case")
    public ResponseEntity<ApiResponse<Void>> deleteTestCase(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        testSuiteService.deleteTestCase(id, principal);
        return ResponseEntity.ok(ApiResponse.ok(null, "Test case deleted successfully"));
    }
}
