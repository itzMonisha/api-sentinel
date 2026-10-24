package com.sentinel.modules.project.service;

import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectMember;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectMemberRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.shared.exception.ResourceNotFoundException;
import com.sentinel.shared.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSecurityServiceUnitTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private ApiEndpointRepository apiEndpointRepository;

    private ProjectSecurityService securityService;

    private UUID projectId;
    private UUID userId;
    private Project project;
    private User user;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        securityService = new ProjectSecurityService(
                projectRepository,
                projectMemberRepository,
                apiEndpointRepository
        );

        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = new User("alice@apisentinel.dev", "hash", "Alice", Role.ROLE_USER);
        user.setId(userId);
        userPrincipal = new UserPrincipal(
                userId,
                "alice@apisentinel.dev",
                "hash",
                Role.ROLE_USER,
                true
        );

        project = new Project("Payments Core", "payments-core", "Core processing", user);
        project.setId(projectId);
    }

    @Test
    @DisplayName("Should grant access when member has OWNER role")
    void shouldGrantAccessWhenOwner() {
        ProjectMember member = new ProjectMember(project, user, ProjectRole.OWNER);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(member));

        Project result = securityService.getAuthorizedProject(projectId, userPrincipal, ProjectRole.OWNER);
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Payments Core");
    }

    @Test
    @DisplayName("Should grant access when member has EDITOR role and min role is EDITOR")
    void shouldGrantAccessWhenEditor() {
        ProjectMember member = new ProjectMember(project, user, ProjectRole.EDITOR);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(member));

        Project result = securityService.getAuthorizedProject(projectId, userPrincipal, ProjectRole.EDITOR);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should deny access when VIEWER attempts EDITOR-level mutation")
    void shouldDenyViewerAttemptingEditorAction() {
        ProjectMember member = new ProjectMember(project, user, ProjectRole.VIEWER);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> securityService.getAuthorizedProject(projectId, userPrincipal, ProjectRole.EDITOR))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient permissions");
    }

    @Test
    @DisplayName("Should deny access when user is not a member of the project")
    void shouldDenyNonMember() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> securityService.getAuthorizedProject(projectId, userPrincipal, ProjectRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not a member of project");
    }

    @Test
    @DisplayName("Should bypass project membership check for global ROLE_ADMIN")
    void shouldAllowGlobalAdmin() {
        UserPrincipal adminPrincipal = new UserPrincipal(
                UUID.randomUUID(),
                "admin@apisentinel.dev",
                "hash",
                Role.ROLE_ADMIN,
                true
        );

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));

        Project result = securityService.getAuthorizedProject(projectId, adminPrincipal, ProjectRole.OWNER);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Should deny access when principal is null (unauthenticated)")
    void shouldDenyUnauthenticated() {
        assertThatThrownBy(() -> securityService.getAuthorizedProject(projectId, null, ProjectRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when project does not exist")
    void shouldThrowWhenProjectNotFound() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> securityService.getAuthorizedProject(projectId, userPrincipal, ProjectRole.VIEWER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    @DisplayName("Should validate endpoint belongs to authorized project")
    void shouldVerifyEndpointBelongsToProject() {
        UUID endpointId = UUID.randomUUID();
        ApiEndpoint endpoint = new ApiEndpoint(project, "Status Endpoint", "https://api.example.com/status", "GET");

        ProjectMember member = new ProjectMember(project, user, ProjectRole.VIEWER);
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(member));
        when(apiEndpointRepository.findByIdAndDeletedAtIsNull(endpointId)).thenReturn(Optional.of(endpoint));

        ApiEndpoint result = securityService.getAuthorizedEndpoint(projectId, endpointId, userPrincipal, ProjectRole.VIEWER);
        assertThat(result).isNotNull();
        assertThat(result.getUrl()).isEqualTo("https://api.example.com/status");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException if endpoint belongs to different project")
    void shouldThrowIfEndpointBelongsToDifferentProject() {
        UUID endpointId = UUID.randomUUID();
        Project otherProject = new Project("Other Project", "other-project", "Other desc", user);
        otherProject.setId(UUID.randomUUID());
        ApiEndpoint endpoint = new ApiEndpoint(otherProject, "Other Endpoint", "https://other.example.com", "GET");

        ProjectMember member = new ProjectMember(project, user, ProjectRole.VIEWER);
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(member));
        when(apiEndpointRepository.findByIdAndDeletedAtIsNull(endpointId)).thenReturn(Optional.of(endpoint));

        assertThatThrownBy(() -> securityService.getAuthorizedEndpoint(projectId, endpointId, userPrincipal, ProjectRole.VIEWER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("does not belong to project");
    }
}
