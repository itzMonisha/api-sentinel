package com.sentinel.modules.project.controller;

import com.sentinel.modules.project.dto.CreateProjectRequest;
import com.sentinel.modules.project.dto.ProjectResponse;
import com.sentinel.modules.project.dto.UpdateProjectRequest;
import com.sentinel.modules.project.service.ProjectService;
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
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Multi-tenant project workspace management")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "Create a new project workspace")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProjectResponse response = projectService.createProject(request, principal);
        return new ResponseEntity<>(
                ApiResponse.created(response, "Project created successfully"),
                HttpStatus.CREATED
        );
    }

    @GetMapping
    @Operation(summary = "List projects accessible to the current user (paginated with search filter)")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> listProjects(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String[] sortParts = sort.split(",");
        String sortProp = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), Sort.by(direction, sortProp));
        PageResponse<ProjectResponse> response = projectService.listProjects(principal, search, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project details by ID")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProjectResponse response = projectService.getProject(projectId, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update project details (requires OWNER role)")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ProjectResponse response = projectService.updateProject(projectId, request, principal);
        return ResponseEntity.ok(ApiResponse.ok(response, "Project updated successfully"));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete project workspace (requires OWNER role)")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        projectService.deleteProject(projectId, principal);
        return ResponseEntity.ok(ApiResponse.ok(null, "Project deleted successfully"));
    }
}
