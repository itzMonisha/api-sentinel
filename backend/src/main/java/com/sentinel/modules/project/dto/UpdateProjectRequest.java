package com.sentinel.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(min = 2, max = 120, message = "Project name must be between 2 and 120 characters")
        String name,

        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description
) {
}
