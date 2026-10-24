package com.sentinel.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(min = 2, max = 120, message = "Project name must be between 2 and 120 characters")
        String name,

        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        @Size(min = 2, max = 140, message = "Slug must be between 2 and 140 characters")
        String slug,

        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description
) {
}
