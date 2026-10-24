package com.sentinel.modules.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.project.dto.AuthConfigDto;
import com.sentinel.modules.project.dto.CreateEndpointRequest;
import com.sentinel.modules.project.dto.EndpointResponse;
import com.sentinel.modules.project.dto.ParameterDto;
import com.sentinel.modules.project.dto.TestHistorySummaryDto;
import com.sentinel.modules.project.dto.UpdateEndpointRequest;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.shared.exception.DuplicateResourceException;
import com.sentinel.shared.model.PageResponse;
import com.sentinel.shared.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ApiEndpointService {

    private static final Logger log = LoggerFactory.getLogger(ApiEndpointService.class);

    private final ApiEndpointRepository apiEndpointRepository;
    private final ProjectSecurityService projectSecurityService;
    private final ObjectMapper objectMapper;

    public ApiEndpointService(
            ApiEndpointRepository apiEndpointRepository,
            ProjectSecurityService projectSecurityService,
            ObjectMapper objectMapper
    ) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.projectSecurityService = projectSecurityService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EndpointResponse createEndpoint(UUID projectId, CreateEndpointRequest request, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.EDITOR);

        String method = request.httpMethod().trim().toUpperCase(Locale.ROOT);
        String url = request.url().trim();

        if (apiEndpointRepository.existsByProjectIdAndHttpMethodAndUrlAndDeletedAtIsNull(projectId, method, url)) {
            throw new DuplicateResourceException(String.format(
                    "Endpoint [%s %s] already exists in project '%s'",
                    method, url, project.getName()
            ));
        }

        ApiEndpoint endpoint = new ApiEndpoint(project, request.name().trim(), url, method);
        endpoint.setDescription(request.description());
        endpoint.setOperationId(request.operationId());

        if (request.authConfig() != null) {
            AuthConfigDto safeConfig = AuthConfigDto.of(
                    request.authConfig().type(),
                    request.authConfig().keyName(),
                    request.authConfig().headerName(),
                    request.authConfig().paramLocation(),
                    request.authConfig().tokenOrPassword()
            );
            endpoint.setAuthConfig(toJson(safeConfig));
        }

        if (request.parameters() != null) {
            endpoint.setParameters(toJson(request.parameters()));
        }
        if (request.headers() != null) {
            endpoint.setHeaders(toJson(request.headers()));
        }
        endpoint.setRequestSchema(request.requestSchema());
        endpoint.setResponseSchema(request.responseSchema());

        ApiEndpoint saved = apiEndpointRepository.save(endpoint);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public EndpointResponse getEndpoint(UUID projectId, UUID endpointId, UserPrincipal principal) {
        ApiEndpoint endpoint = projectSecurityService.getAuthorizedEndpoint(projectId, endpointId, principal, ProjectRole.VIEWER);
        return toDto(endpoint);
    }

    @Transactional(readOnly = true)
    public PageResponse<EndpointResponse> listEndpoints(
            UUID projectId,
            String method,
            String search,
            Pageable pageable,
            UserPrincipal principal
    ) {
        projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);

        Page<ApiEndpoint> page = apiEndpointRepository.findEndpointsByProject(projectId, method, search, pageable);
        return PageResponse.from(page.map(this::toDto));
    }

    @Transactional
    public EndpointResponse updateEndpoint(
            UUID projectId,
            UUID endpointId,
            UpdateEndpointRequest request,
            UserPrincipal principal
    ) {
        ApiEndpoint endpoint = projectSecurityService.getAuthorizedEndpoint(projectId, endpointId, principal, ProjectRole.EDITOR);

        String method = request.httpMethod().trim().toUpperCase(Locale.ROOT);
        String url = request.url().trim();

        boolean urlOrMethodChanged = !endpoint.getHttpMethod().equalsIgnoreCase(method) || !endpoint.getUrl().equals(url);
        if (urlOrMethodChanged && apiEndpointRepository.existsByProjectIdAndHttpMethodAndUrlAndDeletedAtIsNull(projectId, method, url)) {
            throw new DuplicateResourceException(String.format(
                    "Endpoint [%s %s] already exists in project '%s'",
                    method, url, endpoint.getProject().getName()
            ));
        }

        endpoint.setName(request.name().trim());
        endpoint.setUrl(url);
        endpoint.setHttpMethod(method);
        endpoint.setDescription(request.description());
        endpoint.setOperationId(request.operationId());

        if (request.deprecated() != null) {
            endpoint.setDeprecated(request.deprecated());
        }

        if (request.authConfig() != null) {
            AuthConfigDto safeConfig = AuthConfigDto.of(
                    request.authConfig().type(),
                    request.authConfig().keyName(),
                    request.authConfig().headerName(),
                    request.authConfig().paramLocation(),
                    request.authConfig().tokenOrPassword()
            );
            endpoint.setAuthConfig(toJson(safeConfig));
        }

        if (request.parameters() != null) {
            endpoint.setParameters(toJson(request.parameters()));
        }
        if (request.headers() != null) {
            endpoint.setHeaders(toJson(request.headers()));
        }
        endpoint.setRequestSchema(request.requestSchema());
        endpoint.setResponseSchema(request.responseSchema());

        ApiEndpoint updated = apiEndpointRepository.save(endpoint);
        return toDto(updated);
    }

    @Transactional
    public void deleteEndpoint(UUID projectId, UUID endpointId, UserPrincipal principal) {
        ApiEndpoint endpoint = projectSecurityService.getAuthorizedEndpoint(projectId, endpointId, principal, ProjectRole.EDITOR);
        endpoint.markDeleted();
        apiEndpointRepository.save(endpoint);
    }

    public EndpointResponse toDto(ApiEndpoint endpoint) {
        AuthConfigDto authConfig = null;
        if (endpoint.getAuthConfig() != null && !endpoint.getAuthConfig().isBlank()) {
            authConfig = fromJson(endpoint.getAuthConfig(), AuthConfigDto.class);
        }

        List<ParameterDto> parameters = null;
        if (endpoint.getParameters() != null && !endpoint.getParameters().isBlank()) {
            parameters = fromJson(endpoint.getParameters(), new TypeReference<List<ParameterDto>>() {});
        }

        Map<String, String> headers = null;
        if (endpoint.getHeaders() != null && !endpoint.getHeaders().isBlank()) {
            headers = fromJson(endpoint.getHeaders(), new TypeReference<Map<String, String>>() {});
        }

        // Test history is initially empty until test suite executions run in later phases
        List<TestHistorySummaryDto> testHistory = Collections.emptyList();

        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getProject().getId(),
                endpoint.getName(),
                endpoint.getUrl(),
                endpoint.getHttpMethod(),
                endpoint.getDescription(),
                endpoint.getOperationId(),
                authConfig,
                parameters,
                headers,
                endpoint.getRequestSchema(),
                endpoint.getResponseSchema(),
                endpoint.isDeprecated(),
                testHistory,
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON", e);
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize JSON to {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize JSON with type reference: {}", e.getMessage());
            return null;
        }
    }
}
