package com.sentinel.modules.project.controller;

import com.sentinel.modules.project.dto.OpenApiImportRequest;
import com.sentinel.modules.project.dto.OpenApiImportResponse;
import com.sentinel.modules.project.dto.OpenApiPreviewResponse;
import com.sentinel.modules.project.service.OpenApiParserService;
import com.sentinel.shared.model.ApiResponse;
import com.sentinel.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/openapi")
@Tag(name = "OpenAPI Ingestion", description = "OpenAPI 3.x specification validation, discovery, and import")
public class OpenApiController {

    private final OpenApiParserService openApiParserService;

    public OpenApiController(OpenApiParserService openApiParserService) {
        this.openApiParserService = openApiParserService;
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview endpoints from raw OpenAPI JSON/YAML specification payload")
    public ResponseEntity<ApiResponse<OpenApiPreviewResponse>> previewRawSpec(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        String rawSpec = payload.get("spec");
        if (rawSpec == null) {
            rawSpec = payload.get("rawSpec");
        }
        OpenApiPreviewResponse response = openApiParserService.previewSpecification(projectId, rawSpec, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping(value = "/preview/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Preview endpoints from uploaded OpenAPI JSON or YAML file")
    public ResponseEntity<ApiResponse<OpenApiPreviewResponse>> previewUploadedFile(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OpenApiPreviewResponse response = openApiParserService.previewSpecification(projectId, file, principal);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/import")
    @Operation(summary = "Import discovered endpoints into project from raw specification")
    public ResponseEntity<ApiResponse<OpenApiImportResponse>> importRawSpec(
            @PathVariable UUID projectId,
            @RequestBody OpenApiImportRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OpenApiImportResponse response = openApiParserService.importSpecification(
                projectId,
                request.rawSpec(),
                request,
                principal
        );
        return ResponseEntity.ok(ApiResponse.ok(response, "OpenAPI specification imported successfully"));
    }

    @PostMapping(value = "/import/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import discovered endpoints into project from uploaded file")
    public ResponseEntity<ApiResponse<OpenApiImportResponse>> importUploadedFile(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean overwriteExisting,
            @RequestParam(required = false) List<String> selectedKeys,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OpenApiImportRequest request = new OpenApiImportRequest(null, selectedKeys, overwriteExisting, null);
        OpenApiImportResponse response = openApiParserService.importSpecification(
                projectId,
                file,
                request,
                principal
        );
        return ResponseEntity.ok(ApiResponse.ok(response, "OpenAPI file imported successfully"));
    }
}
