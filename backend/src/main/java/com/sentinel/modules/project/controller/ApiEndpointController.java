package com.sentinel.modules.project.controller;

import com.sentinel.modules.project.dto.CreateEndpointRequest;
import com.sentinel.modules.project.dto.EndpointResponse;
import com.sentinel.modules.project.dto.UpdateEndpointRequest;
import com.sentinel.modules.project.service.ApiEndpointService;
import com.sentinel.shared.model.ApiResponse;
import com.sentinel.shared.model.PageResponse;
import com.sentinel.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/endpoints")
@Tag(name = "API Endpoints", description = "Endpoint registration, cataloging, and schemas")
public class ApiEndpointController {

    private final ApiEndpointService apiEndpointService;

    public ApiEndpointController(ApiEndpointService apiEndpointService) {
        this.apiEndpointService = apiEndpointService;
    }

    @PostMapping
    @Operation(summary = "Register a new API endpoint under a project")
    public ResponseEntity<ApiResponse<EndpointResponse>> createEndpoint(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEndpointRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EndpointResponse response = apiEndpointService.createEndpoint(projectId, request, principal);
        return new ResponseEntity<>(
                ApiResponse.created(response, "Endpoint registered successfully"),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    @Operation(summary = "List endpoints registered under a project (paginated with method & search filters)")
    public ResponseEntity<ApiResponse<PageResponse<EndpointResponse>>> listEndpoints(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String[] sortParts = sort.split(",");
        String sortProp = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(direction, sortProp));
        PageResponse<EndpointResponse> response = apiEndpointService.listEndpoints(projectId, method, search, pageable, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{endpointId}")
    @Operation(summary = "View endpoint details, schemas, and execution history")
    public ResponseEntity<ApiResponse<EndpointResponse>> getEndpoint(
            @PathVariable UUID projectId,
            @PathVariable UUID endpointId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EndpointResponse response = apiEndpointService.getEndpoint(projectId, endpointId, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{endpointId}")
    @Operation(summary = "Update registered endpoint configuration")
    public ResponseEntity<ApiResponse<EndpointResponse>> updateEndpoint(
            @PathVariable UUID projectId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody UpdateEndpointRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        EndpointResponse response = apiEndpointService.updateEndpoint(projectId, endpointId, request, principal);
        return ResponseEntity.ok(ApiResponse.ok(response, "Endpoint updated successfully"));
    }

    @DeleteMapping("/{endpointId}")
    @Operation(summary = "Delete registered endpoint from project")
    public ResponseEntity<ApiResponse<Void>> deleteEndpoint(
            @PathVariable UUID projectId,
            @PathVariable UUID endpointId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        apiEndpointService.deleteEndpoint(projectId, endpointId, principal);
        return ResponseEntity.ok(ApiResponse.ok(null, "Endpoint deleted successfully"));
    }
}
