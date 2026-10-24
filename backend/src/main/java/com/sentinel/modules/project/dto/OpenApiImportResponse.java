package com.sentinel.modules.project.dto;

import java.util.List;

public record OpenApiImportResponse(
        int totalProcessed,
        int importedCount,
        int skippedCount,
        int overwrittenCount,
        List<EndpointResponse> importedEndpoints
) {
}
