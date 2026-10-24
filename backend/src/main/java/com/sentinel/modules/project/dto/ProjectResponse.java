package com.sentinel.modules.project.dto;

import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String slug,
        String description,
        UUID createdBy,
        ProjectRole userRole,
        int endpointCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project, ProjectRole role, int endpointCount) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getSlug(),
                project.getDescription(),
                project.getCreatedBy() != null ? project.getCreatedBy().getId() : null,
                role,
                endpointCount,
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
