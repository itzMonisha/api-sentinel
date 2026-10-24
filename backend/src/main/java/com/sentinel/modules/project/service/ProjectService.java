package com.sentinel.modules.project.service;

import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.project.dto.CreateProjectRequest;
import com.sentinel.modules.project.dto.ProjectResponse;
import com.sentinel.modules.project.dto.UpdateProjectRequest;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.shared.exception.DuplicateResourceException;
import com.sentinel.shared.exception.ResourceNotFoundException;
import com.sentinel.shared.model.PageResponse;
import com.sentinel.shared.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ProjectSecurityService projectSecurityService;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ApiEndpointRepository apiEndpointRepository,
            ProjectSecurityService projectSecurityService
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.apiEndpointRepository = apiEndpointRepository;
        this.projectSecurityService = projectSecurityService;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request, UserPrincipal principal) {
        User creator = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + principal.getId()));

        String slug = request.slug();
        if (slug == null || slug.isBlank()) {
            slug = generateSlug(request.name());
        } else {
            slug = slug.toLowerCase(Locale.ROOT).trim();
        }

        if (projectRepository.existsBySlugAndDeletedAtIsNull(slug)) {
            // If custom slug was provided, reject. If auto-generated, make unique.
            if (request.slug() != null && !request.slug().isBlank()) {
                throw new DuplicateResourceException("Project with slug '" + slug + "' already exists");
            }
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 6);
        }

        Project project = new Project(request.name().trim(), slug, request.description(), creator);
        project.addMember(creator, ProjectRole.OWNER);

        Project saved = projectRepository.save(project);
        return ProjectResponse.from(saved, ProjectRole.OWNER, 0);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.VIEWER);
        ProjectRole role = projectSecurityService.getUserRole(projectId, principal);
        int endpointCount = apiEndpointRepository.countByProjectIdAndDeletedAtIsNull(projectId);
        return ProjectResponse.from(project, role, endpointCount);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listProjects(UserPrincipal principal, String search, Pageable pageable) {
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));
        Page<Project> page;
        if (isAdmin) {
            page = projectRepository.findAllActive(search, pageable);
        } else {
            page = projectRepository.findProjectsForUser(principal.getId(), search, pageable);
        }

        Page<ProjectResponse> dtoPage = page.map(project -> {
            ProjectRole role = projectSecurityService.getUserRole(project.getId(), principal);
            int endpointCount = apiEndpointRepository.countByProjectIdAndDeletedAtIsNull(project.getId());
            return ProjectResponse.from(project, role, endpointCount);
        });

        return PageResponse.from(dtoPage);
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, UpdateProjectRequest request, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.OWNER);

        project.setName(request.name().trim());
        project.setDescription(request.description());

        Project updated = projectRepository.save(project);
        ProjectRole role = projectSecurityService.getUserRole(projectId, principal);
        int endpointCount = apiEndpointRepository.countByProjectIdAndDeletedAtIsNull(projectId);
        return ProjectResponse.from(updated, role, endpointCount);
    }

    @Transactional
    public void deleteProject(UUID projectId, UserPrincipal principal) {
        Project project = projectSecurityService.getAuthorizedProject(projectId, principal, ProjectRole.OWNER);

        // Soft delete project
        project.markDeleted();

        // Soft delete all endpoints under the project
        for (ApiEndpoint endpoint : apiEndpointRepository.findAllByProjectIdAndDeletedAtIsNull(projectId)) {
            endpoint.markDeleted();
            apiEndpointRepository.save(endpoint);
        }

        projectRepository.save(project);
    }

    private String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "project-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String slug = normalized
                .replaceAll("[^a-zA-Z0-9\\s-]", " ")
                .trim()
                .replaceAll("\\s+", "-")
                .toLowerCase(Locale.ENGLISH);
        slug = slug.replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (slug.isBlank()) {
            slug = "project-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return slug;
    }
}
