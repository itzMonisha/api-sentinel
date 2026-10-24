package com.sentinel.modules.project.dto;

import java.time.Instant;
import java.util.UUID;

public record TestHistorySummaryDto(
        UUID testRunId,
        String testCaseName,
        String status,
        int httpStatusCode,
        long responseTimeMs,
        Instant executedAt
) {
}
