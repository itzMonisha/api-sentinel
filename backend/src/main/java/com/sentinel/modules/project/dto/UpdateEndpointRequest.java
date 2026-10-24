package com.sentinel.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record UpdateEndpointRequest(
        @NotBlank(message = "Endpoint name is required")
        @Size(min = 1, max = 255, message = "Endpoint name cannot exceed 255 characters")
        String name,

        @NotBlank(message = "Endpoint URL or path is required")
        @Size(max = 1000, message = "URL cannot exceed 1000 characters")
        @Pattern(
                regexp = "^(https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/[\\S]*)?|/[\\S]*)$",
                message = "URL must be a valid HTTP/HTTPS URL (e.g. https://api.example.com/v1/users) or an absolute path starting with / (e.g. /v1/users)"
        )
        String url,

        @NotBlank(message = "HTTP method is required")
        @Pattern(
                regexp = "^(?i)(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)$",
                message = "HTTP method must be one of: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS"
        )
        String httpMethod,

        @Size(max = 4000, message = "Description cannot exceed 4000 characters")
        String description,

        String operationId,

        AuthConfigDto authConfig,

        List<ParameterDto> parameters,

        Map<String, String> headers,

        String requestSchema,

        String responseSchema,

        Boolean deprecated
) {
}
