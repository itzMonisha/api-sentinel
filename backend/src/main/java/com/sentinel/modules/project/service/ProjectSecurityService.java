package com.sentinel.modules.project.service;

import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectMember;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectMemberRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.shared.exception.ResourceNotFoundException;
import com.sentinel.shared.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProjectSecurityService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ApiEndpointRepository apiEndpointRepository;

    public ProjectSecurityService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            ApiEndpointRepository apiEndpointRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.apiEndpointRepository = apiEndpointRepository;
    }

    public Project getAuthorizedProject(UUID projectId, UserPrincipal principal, ProjectRole minRole) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication is required to access project resources.");
        }

        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        // Global admins have OWNER privilege
        if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()))) {
            return project;
        }

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, principal.getId())
                .orElseThrow(() -> new AccessDeniedException("You are not a member of project: " + project.getName()));

        if (!hasSufficientRole(member.getRole(), minRole)) {
            throw new AccessDeniedException(String.format(
                    "Insufficient permissions. Required role: %s, your role: %s",
                    minRole,
                    member.getRole()
            ));
        }

        return project;
    }

    public ApiEndpoint getAuthorizedEndpoint(UUID projectId, UUID endpointId, UserPrincipal principal, ProjectRole minRole) {
        Project project = getAuthorizedProject(projectId, principal, minRole);

        ApiEndpoint endpoint = apiEndpointRepository.findByIdAndDeletedAtIsNull(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found with id: " + endpointId));

        if (!endpoint.getProject().getId().equals(project.getId())) {
            throw new ResourceNotFoundException("Endpoint does not belong to project: " + project.getName());
        }

        return endpoint;
    }

    public ProjectRole getUserRole(UUID projectId, UserPrincipal principal) {
        if (principal == null) {
            return null;
        }
        if (principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()))) {
            return ProjectRole.OWNER;
        }
        return projectMemberRepository.findByProjectIdAndUserId(projectId, principal.getId())
                .map(ProjectMember::getRole)
                .orElse(null);
    }

    private boolean hasSufficientRole(ProjectRole actual, ProjectRole required) {
        if (required == null || required == ProjectRole.VIEWER) {
            return true;
        }
        if (required == ProjectRole.EDITOR) {
            return actual == ProjectRole.OWNER || actual == ProjectRole.EDITOR;
        }
        if (required == ProjectRole.OWNER) {
            return actual == ProjectRole.OWNER;
        }
        return false;
    }
}
