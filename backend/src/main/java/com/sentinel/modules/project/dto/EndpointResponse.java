package com.sentinel.modules.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EndpointResponse(
        UUID id,
        UUID projectId,
        String name,
        String url,
        String httpMethod,
        String description,
        String operationId,
        AuthConfigDto authConfig,
        List<ParameterDto> parameters,
        Map<String, String> headers,
        String requestSchema,
        String responseSchema,
        boolean deprecated,
        List<TestHistorySummaryDto> testHistory,
        Instant createdAt,
        Instant updatedAt
) {
}
