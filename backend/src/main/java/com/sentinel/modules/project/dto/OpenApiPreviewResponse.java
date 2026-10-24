package com.sentinel.modules.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenApiPreviewResponse(
        String title,
        String version,
        String description,
        List<String> serverUrls,
        int totalEndpoints,
        int existingEndpointsCount,
        int newEndpointsCount,
        List<OpenApiEndpointPreview> endpoints,
        List<String> validationErrors,
        List<String> validationWarnings
) {
}
