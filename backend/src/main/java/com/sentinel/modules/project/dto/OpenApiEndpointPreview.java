package com.sentinel.modules.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenApiEndpointPreview(
        String key,
        String name,
        String path,
        String httpMethod,
        String summary,
        String description,
        String operationId,
        List<String> tags,
        boolean alreadyExists,
        List<ParameterDto> parameters,
        String requestSchema,
        String responseSchema,
        boolean deprecated
) {
}
