package com.sentinel.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.project.dto.CreateEndpointRequest;
import com.sentinel.modules.project.dto.CreateProjectRequest;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectMember;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectMemberRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.modules.testing.repository.TestCaseRepository;
import com.sentinel.modules.testing.repository.TestResultRepository;
import com.sentinel.modules.testing.repository.TestRunRepository;
import com.sentinel.modules.testing.repository.TestSuiteRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAuthTokenAndEscalationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    @Autowired(required = false)
    private TestResultRepository testResultRepository;

    @Autowired(required = false)
    private TestRunRepository testRunRepository;

    @Autowired(required = false)
    private TestCaseRepository testCaseRepository;

    @Autowired(required = false)
    private TestSuiteRepository testSuiteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Value("${api-sentinel.jwt.secret:default-secret-key-must-be-very-long-and-secure-for-hmac-sha-256}")
    private String jwtSecret;

    private User userAlice;
    private User userBob;
    private String tokenAlice;
    private String tokenBob;

    @BeforeEach
    void setUp() {
        if (testResultRepository != null) {
            testResultRepository.deleteAll();
        }
        if (testRunRepository != null) {
            testRunRepository.deleteAll();
        }
        if (testCaseRepository != null) {
            testCaseRepository.deleteAll();
        }
        if (testSuiteRepository != null) {
            testSuiteRepository.deleteAll();
        }
        apiEndpointRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        userAlice = userRepository.save(new User(
                "alice.audit@apisentinel.dev",
                passwordEncoder.encode("Password123!"),
                "Alice Auditor",
                Role.ROLE_USER
        ));
        tokenAlice = tokenProvider.generateAccessToken(userAlice);

        userBob = userRepository.save(new User(
                "bob.audit@apisentinel.dev",
                passwordEncoder.encode("Password123!"),
                "Bob Auditor",
                Role.ROLE_USER
        ));
        tokenBob = tokenProvider.generateAccessToken(userBob);
    }

    @Nested
    @DisplayName("Token Validity & Anomaly Tests")
    class TokenValidityTests {

        @Test
        @DisplayName("Unauthenticated request to protected endpoint returns 401")
        void shouldRejectMissingToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Malformed JWT token returns 401")
        void shouldRejectMalformedToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer not.a.valid.jwt.token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token signed with untrusted foreign key returns 401")
        void shouldRejectTamperedSignature() throws Exception {
            SecretKey attackerKey = Keys.hmacShaKeyFor(
                    "evil-attacker-secret-key-must-be-very-long-and-secure-32bytes".getBytes(StandardCharsets.UTF_8)
            );
            String forgedToken = Jwts.builder()
                    .subject(userAlice.getId().toString())
                    .claim("email", userAlice.getEmail())
                    .claim("role", "ROLE_ADMIN")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60000))
                    .signWith(attackerKey)
                    .compact();

            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + forgedToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Expired JWT token returns 401")
        void shouldRejectExpiredToken() throws Exception {
            SecretKey validKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            String expiredToken = Jwts.builder()
                    .subject(userAlice.getId().toString())
                    .claim("email", userAlice.getEmail())
                    .claim("role", "ROLE_USER")
                    .issuedAt(new Date(System.currentTimeMillis() - 100000))
                    .expiration(new Date(System.currentTimeMillis() - 1000)) // expired 1s ago
                    .signWith(validKey)
                    .compact();

            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Cross-User Object-Level Authorization Tests")
    class CrossUserAuthorizationTests {

        @Test
        @DisplayName("User Bob cannot read Alice's project (403 Forbidden)")
        void shouldPreventBobFromAccessingAliceProject() throws Exception {
            // Alice creates Project A
            CreateProjectRequest req = new CreateProjectRequest(
                    "Alice Confidential Project",
                    "alice-confidential",
                    "Top secret workspace"
            );
            MvcResult res = mockMvc.perform(post("/api/v1/projects")
                            .header("Authorization", "Bearer " + tokenAlice)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String projectId = objectMapper.readTree(res.getResponse().getContentAsString())
                    .path("data").path("id").asText();

            // Bob attempts to read Alice's project
            mockMvc.perform(get("/api/v1/projects/" + projectId)
                            .header("Authorization", "Bearer " + tokenBob))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("User Bob cannot add endpoints to Alice's project (403 Forbidden)")
        void shouldPreventBobFromAddingEndpointsToAliceProject() throws Exception {
            Project aliceProject = projectRepository.save(new Project("Alice Vault", "alice-vault", "Desc", userAlice));
            projectMemberRepository.save(new ProjectMember(aliceProject, userAlice, ProjectRole.OWNER));

            CreateEndpointRequest epReq = new CreateEndpointRequest(
                    "Injected Endpoint",
                    "https://api.example.com/injected",
                    "POST",
                    "Injected desc",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            mockMvc.perform(post("/api/v1/projects/" + aliceProject.getId() + "/endpoints")
                            .header("Authorization", "Bearer " + tokenBob)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(epReq)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Role-Based Privilege Escalation Tests")
    class PrivilegeEscalationTests {

        @Test
        @DisplayName("Viewer member cannot delete a project (403 Forbidden)")
        void shouldPreventViewerFromDeletingProject() throws Exception {
            Project project = projectRepository.save(new Project("Shared Project", "shared-project", "Desc", userAlice));
            projectMemberRepository.save(new ProjectMember(project, userAlice, ProjectRole.OWNER));

            // Add Bob as VIEWER
            projectMemberRepository.save(new ProjectMember(project, userBob, ProjectRole.VIEWER));

            // Bob can read
            mockMvc.perform(get("/api/v1/projects/" + project.getId())
                            .header("Authorization", "Bearer " + tokenBob))
                    .andExpect(status().isOk());

            // Bob attempts DELETE
            mockMvc.perform(delete("/api/v1/projects/" + project.getId())
                            .header("Authorization", "Bearer " + tokenBob))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Viewer member cannot create new endpoints in project (403 Forbidden)")
        void shouldPreventViewerFromCreatingEndpoints() throws Exception {
            Project project = projectRepository.save(new Project("Team Repo", "team-repo", "Desc", userAlice));
            projectMemberRepository.save(new ProjectMember(project, userAlice, ProjectRole.OWNER));
            projectMemberRepository.save(new ProjectMember(project, userBob, ProjectRole.VIEWER));

            CreateEndpointRequest epReq = new CreateEndpointRequest(
                    "Unauthorized Endpoint",
                    "https://api.example.com/unauthorized",
                    "GET",
                    "Viewer attempting write",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/endpoints")
                            .header("Authorization", "Bearer " + tokenBob)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(epReq)))
                    .andExpect(status().isForbidden());
        }
    }
}
