package com.sentinel.modules.project.dto;

import java.util.List;

public record OpenApiImportRequest(
        String rawSpec,
        List<String> selectedKeys,
        boolean overwriteExisting,
        String baseUrlOverride
) {
}
