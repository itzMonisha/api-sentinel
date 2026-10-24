package com.sentinel.modules.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.project.dto.CreateEndpointRequest;
import com.sentinel.modules.project.dto.EndpointResponse;
import com.sentinel.modules.project.dto.OpenApiEndpointPreview;
import com.sentinel.modules.project.dto.OpenApiImportRequest;
import com.sentinel.modules.project.dto.OpenApiImportResponse;
import com.sentinel.modules.project.dto.OpenApiPreviewResponse;
import com.sentinel.modules.project.dto.ParameterDto;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.shared.exception.ValidationException;
import com.sentinel.shared.security.UserPrincipal;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class OpenApiParserService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiParserService.class);

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    public static final int MAX_ENDPOINTS_LIMIT = 500;

    private final ApiEndpointRepository apiEndpointRepository;
    private final ProjectSecurityService projectSecurityService;
    private final ApiEndpointService apiEndpointService;
    private final ObjectMapper objectMapper;

    public OpenApiParserService(
            ApiEndpointRepository apiEndpointRepository,
            ProjectSecurityService projectSecurityService,
            ApiEndpointService apiEndpointService,
            ObjectMapper objectMapper
    ) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.projectSecurityService = projectSecurityService;
        this.apiEndpointService = apiEndpointService;
        this.objectMapper = objectMapper;
    }

    public OpenApiPreviewResponse previewSpecification(UUID projectId, String specContent, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);
        return parseAndPreview(project, specContent);
    }

    public OpenApiPreviewResponse previewSpecification(UUID projectId, MultipartFile file, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);
        String specContent = extractFileContent(file);
        return parseAndPreview(project, specContent);
    }

    @Transactional
    public OpenApiImportResponse importSpecification(
            UUID projectId,
            String specContent,
            OpenApiImportRequest request,
            UserPrincipal principal
    ) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.EDITOR);
        return executeImport(project, specContent, request);
    }

    @Transactional
    public OpenApiImportResponse importSpecification(
            UUID projectId,
            MultipartFile file,
            OpenApiImportRequest request,
            UserPrincipal principal
    ) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.EDITOR);
        String specContent = extractFileContent(file);
        return executeImport(project, specContent, request);
    }

    private OpenApiPreviewResponse parseAndPreview(Project project, String specContent) {
        validateSpecContent(specContent);

        OpenAPI openAPI = parseOpenApi(specContent);

        String title = openAPI.getInfo() != null && openAPI.getInfo().getTitle() != null
                ? openAPI.getInfo().getTitle()
                : "Imported OpenAPI Specification";
        String version = openAPI.getInfo() != null && openAPI.getInfo().getVersion() != null
                ? openAPI.getInfo().getVersion()
                : "1.0.0";
        String description = openAPI.getInfo() != null ? openAPI.getInfo().getDescription() : null;

        List<String> serverUrls = new ArrayList<>();
        if (openAPI.getServers() != null) {
            for (Server server : openAPI.getServers()) {
                if (server.getUrl() != null && !server.getUrl().isBlank()) {
                    serverUrls.add(server.getUrl());
                }
            }
        }

        List<OpenApiEndpointPreview> previews = extractEndpoints(project.getId(), openAPI);

        int existingCount = (int) previews.stream().filter(OpenApiEndpointPreview::alreadyExists).count();
        int newCount = previews.size() - existingCount;

        return new OpenApiPreviewResponse(
                title,
                version,
                description,
                serverUrls,
                previews.size(),
                existingCount,
                newCount,
                previews,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private OpenApiImportResponse executeImport(Project project, String specContent, OpenApiImportRequest request) {
        OpenApiPreviewResponse preview = parseAndPreview(project, specContent);

        Set<String> selectedKeys = request != null && request.selectedKeys() != null && !request.selectedKeys().isEmpty()
                ? new HashSet<>(request.selectedKeys())
                : null;
        boolean overwrite = request != null && request.overwriteExisting();

        int importedCount = 0;
        int skippedCount = 0;
        int overwrittenCount = 0;
        List<EndpointResponse> resultEndpoints = new ArrayList<>();

        for (OpenApiEndpointPreview item : preview.endpoints()) {
            if (selectedKeys != null && !selectedKeys.contains(item.key())) {
                continue;
            }

            String method = item.httpMethod().toUpperCase(Locale.ROOT);
            String url = item.path();

            Optional<ApiEndpoint> existingOpt = apiEndpointRepository
                    .findByProjectIdAndHttpMethodAndUrlAndDeletedAtIsNull(project.getId(), method, url);

            if (existingOpt.isPresent()) {
                if (!overwrite) {
                    skippedCount++;
                    continue;
                }

                // Update existing endpoint
                ApiEndpoint existing = existingOpt.get();
                existing.setName(item.name());
                existing.setDescription(item.description());
                existing.setOperationId(item.operationId());
                existing.setDeprecated(item.deprecated());
                if (item.parameters() != null) {
                    existing.setParameters(toJson(item.parameters()));
                }
                existing.setRequestSchema(item.requestSchema());
                existing.setResponseSchema(item.responseSchema());

                ApiEndpoint saved = apiEndpointRepository.save(existing);
                resultEndpoints.add(apiEndpointService.toDto(saved));
                overwrittenCount++;
            } else {
                // Create new endpoint
                ApiEndpoint endpoint = new ApiEndpoint(project, item.name(), url, method);
                endpoint.setDescription(item.description());
                endpoint.setOperationId(item.operationId());
                endpoint.setDeprecated(item.deprecated());
                if (item.parameters() != null) {
                    endpoint.setParameters(toJson(item.parameters()));
                }
                endpoint.setRequestSchema(item.requestSchema());
                endpoint.setResponseSchema(item.responseSchema());

                ApiEndpoint saved = apiEndpointRepository.save(endpoint);
                resultEndpoints.add(apiEndpointService.toDto(saved));
                importedCount++;
            }
        }

        int totalProcessed = importedCount + skippedCount + overwrittenCount;
        return new OpenApiImportResponse(
                totalProcessed,
                importedCount,
                skippedCount,
                overwrittenCount,
                resultEndpoints
        );
    }

    private List<OpenApiEndpointPreview> extractEndpoints(UUID projectId, OpenAPI openAPI) {
        List<OpenApiEndpointPreview> previews = new ArrayList<>();
        if (openAPI.getPaths() == null) {
            return previews;
        }

        int totalOperations = 0;

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null) continue;

            Map<String, Operation> operations = getOperationsMap(pathItem);

            for (Map.Entry<String, Operation> opEntry : operations.entrySet()) {
                totalOperations++;
                if (totalOperations > MAX_ENDPOINTS_LIMIT) {
                    throw new ValidationException(String.format(
                            "Specification exceeds maximum allowed endpoint limit of %d operations.",
                            MAX_ENDPOINTS_LIMIT
                    ));
                }

                String httpMethod = opEntry.getKey().toUpperCase(Locale.ROOT);
                Operation operation = opEntry.getValue();

                String key = httpMethod + ":" + path;
                String name = operation.getSummary() != null && !operation.getSummary().isBlank()
                        ? operation.getSummary().trim()
                        : (httpMethod + " " + path);
                String summary = operation.getSummary();
                String description = operation.getDescription();
                String operationId = operation.getOperationId();
                List<String> tags = operation.getTags() != null ? operation.getTags() : Collections.emptyList();
                boolean deprecated = Boolean.TRUE.equals(operation.getDeprecated());

                // Parameters
                List<ParameterDto> parameters = extractParameters(pathItem, operation);

                // Request Schema
                String requestSchema = extractRequestSchema(operation);

                // Response Schema
                String responseSchema = extractResponseSchema(operation);

                boolean alreadyExists = apiEndpointRepository
                        .existsByProjectIdAndHttpMethodAndUrlAndDeletedAtIsNull(projectId, httpMethod, path);

                previews.add(new OpenApiEndpointPreview(
                        key,
                        name,
                        path,
                        httpMethod,
                        summary,
                        description,
                        operationId,
                        tags,
                        alreadyExists,
                        parameters,
                        requestSchema,
                        responseSchema,
                        deprecated
                ));
            }
        }

        if (totalOperations == 0) {
            throw new ValidationException("OpenAPI specification contains no paths or operations.");
        }

        return previews;
    }

    private Map<String, Operation> getOperationsMap(PathItem pathItem) {
        Map<String, Operation> map = new LinkedHashMap<>();
        if (pathItem.getGet() != null) map.put("GET", pathItem.getGet());
        if (pathItem.getPost() != null) map.put("POST", pathItem.getPost());
        if (pathItem.getPut() != null) map.put("PUT", pathItem.getPut());
        if (pathItem.getDelete() != null) map.put("DELETE", pathItem.getDelete());
        if (pathItem.getPatch() != null) map.put("PATCH", pathItem.getPatch());
        if (pathItem.getHead() != null) map.put("HEAD", pathItem.getHead());
        if (pathItem.getOptions() != null) map.put("OPTIONS", pathItem.getOptions());
        if (pathItem.getTrace() != null) map.put("TRACE", pathItem.getTrace());
        return map;
    }

    private List<ParameterDto> extractParameters(PathItem pathItem, Operation operation) {
        List<ParameterDto> list = new ArrayList<>();
        Map<String, Parameter> combined = new HashMap<>();

        if (pathItem.getParameters() != null) {
            for (Parameter p : pathItem.getParameters()) {
                if (p.getName() != null) combined.put(p.getName(), p);
            }
        }
        if (operation.getParameters() != null) {
            for (Parameter p : operation.getParameters()) {
                if (p.getName() != null) combined.put(p.getName(), p);
            }
        }

        for (Parameter p : combined.values()) {
            String type = "string";
            String example = null;
            if (p.getSchema() != null) {
                type = p.getSchema().getType() != null ? p.getSchema().getType() : "string";
                if (p.getSchema().getExample() != null) {
                    example = p.getSchema().getExample().toString();
                }
            }
            list.add(new ParameterDto(
                    p.getName(),
                    p.getIn() != null ? p.getIn() : "query",
                    Boolean.TRUE.equals(p.getRequired()),
                    type,
                    p.getDescription(),
                    example
            ));
        }

        return list;
    }

    private String extractRequestSchema(Operation operation) {
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null || requestBody.getContent() == null) {
            return null;
        }

        Content content = requestBody.getContent();
        MediaType mediaType = content.get("application/json");
        if (mediaType == null && !content.isEmpty()) {
            mediaType = content.values().iterator().next();
        }

        if (mediaType != null && mediaType.getSchema() != null) {
            return toJson(mediaType.getSchema());
        }

        return null;
    }

    private String extractResponseSchema(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) {
            return null;
        }

        Map<String, Object> responseSummary = new LinkedHashMap<>();
        for (Map.Entry<String, ApiResponse> entry : responses.entrySet()) {
            String statusCode = entry.getKey();
            ApiResponse apiResponse = entry.getValue();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("description", apiResponse.getDescription());

            if (apiResponse.getContent() != null && !apiResponse.getContent().isEmpty()) {
                MediaType mediaType = apiResponse.getContent().get("application/json");
                if (mediaType == null) {
                    mediaType = apiResponse.getContent().values().iterator().next();
                }
                if (mediaType != null && mediaType.getSchema() != null) {
                    item.put("schema", mediaType.getSchema());
                }
            }
            responseSummary.put(statusCode, item);
        }

        return toJson(responseSummary);
    }

    private void validateSpecContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ValidationException("OpenAPI specification is empty. Please provide a valid JSON or YAML document.");
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException(String.format(
                    "Specification file exceeds maximum allowed size of %d MB.",
                    MAX_FILE_SIZE_BYTES / (1024 * 1024)
            ));
        }
    }

    private OpenAPI parseOpenApi(String specContent) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(false); // Prevents unbounded recursion on self-referential schemas

        SwaggerParseResult result = new OpenAPIV3Parser().readContents(specContent, null, options);
        if (result == null || result.getOpenAPI() == null) {
            String errorMsg = "Malformed OpenAPI specification. Please check syntax.";
            if (result != null && result.getMessages() != null && !result.getMessages().isEmpty()) {
                errorMsg = "Malformed OpenAPI specification: " + String.join("; ", result.getMessages());
            }
            throw new ValidationException(errorMsg);
        }

        return result.getOpenAPI();
    }

    private String extractFileContent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Specification file cannot be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException(String.format(
                    "Specification file exceeds maximum allowed size of %d MB.",
                    MAX_FILE_SIZE_BYTES / (1024 * 1024)
            ));
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ValidationException("Failed to read uploaded OpenAPI file: " + e.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize schema to JSON: {}", e.getMessage());
            return null;
        }
    }
}
