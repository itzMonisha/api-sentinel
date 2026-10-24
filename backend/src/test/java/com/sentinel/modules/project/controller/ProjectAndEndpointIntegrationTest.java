package com.sentinel.modules.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.project.dto.AuthConfigDto;
import com.sentinel.modules.project.dto.CreateEndpointRequest;
import com.sentinel.modules.project.dto.CreateProjectRequest;
import com.sentinel.modules.project.dto.OpenApiImportRequest;
import com.sentinel.modules.project.dto.ParameterDto;
import com.sentinel.modules.project.dto.UpdateEndpointRequest;
import com.sentinel.modules.project.dto.UpdateProjectRequest;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectMemberRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.shared.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectAndEndpointIntegrationTest {

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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        apiEndpointRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        userA = new User("alice@apisentinel.dev", passwordEncoder.encode("Password123!@#"), "Alice Engineer", Role.ROLE_USER);
        userA = userRepository.save(userA);
        tokenA = jwtTokenProvider.generateAccessToken(userA);

        userB = new User("bob@apisentinel.dev", passwordEncoder.encode("Password123!@#"), "Bob Engineer", Role.ROLE_USER);
        userB = userRepository.save(userB);
        tokenB = jwtTokenProvider.generateAccessToken(userB);
    }

    private Project createTestProject(String name, String slug, User owner) {
        Project project = new Project(name, slug, "Test project description", owner);
        project.addMember(owner, ProjectRole.OWNER);
        return projectRepository.save(project);
    }

    @Nested
    @DisplayName("Project CRUD & Access Control Tests")
    class ProjectCrudTests {

        @Test
        @DisplayName("Should create project and automatically assign OWNER role to creator")
        void shouldCreateProjectSuccessfully() throws Exception {
            CreateProjectRequest request = new CreateProjectRequest("Payment Gateway API", "payment-api", "Core payment services");

            mockMvc.perform(post("/api/v1/projects")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("Payment Gateway API"))
                    .andExpect(jsonPath("$.data.slug").value("payment-api"))
                    .andExpect(jsonPath("$.data.userRole").value("OWNER"))
                    .andExpect(jsonPath("$.data.endpointCount").value(0));

            assertThat(projectRepository.existsBySlugAndDeletedAtIsNull("payment-api")).isTrue();
        }

        @Test
        @DisplayName("Should auto-generate slug if omitted in project creation")
        void shouldAutoGenerateSlugWhenOmitted() throws Exception {
            CreateProjectRequest request = new CreateProjectRequest("Auth & Identity Service", null, "SSO and tokens");

            mockMvc.perform(post("/api/v1/projects")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.slug", startsWith("auth-identity-service")));
        }

        @Test
        @DisplayName("Should list only projects where user is a member with search and pagination")
        void shouldListOnlyMemberProjects() throws Exception {
            createTestProject("Alice Project Alpha", "alice-alpha", userA);
            createTestProject("Alice Project Beta", "alice-beta", userA);
            createTestProject("Bob Private Project", "bob-private", userB);

            mockMvc.perform(get("/api/v1/projects")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.content[*].slug", containsInAnyOrder("alice-alpha", "alice-beta")))
                    .andExpect(jsonPath("$.data.content[*].slug", not(hasItem("bob-private"))));

            // Test search filter
            mockMvc.perform(get("/api/v1/projects?search=Alpha")
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].slug").value("alice-alpha"));
        }

        @Test
        @DisplayName("Should prevent cross-user project access (BOLA/IDOR protection)")
        void shouldPreventCrossUserProjectAccess() throws Exception {
            Project projectA = createTestProject("Alice Secret Workspace", "alice-secret", userA);

            // Bob attempts to view Alice's project -> 403 Forbidden
            mockMvc.perform(get("/api/v1/projects/" + projectA.getId())
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.title").value("Forbidden"))
                    .andExpect(jsonPath("$.error.errorCode").value("FORBIDDEN"));

            // Bob attempts to update Alice's project -> 403 Forbidden
            UpdateProjectRequest updateReq = new UpdateProjectRequest("Hacked Project", "Hacked");
            mockMvc.perform(put("/api/v1/projects/" + projectA.getId())
                            .header("Authorization", "Bearer " + tokenB)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateReq)))
                    .andExpect(status().isForbidden());

            // Bob attempts to delete Alice's project -> 403 Forbidden
            mockMvc.perform(delete("/api/v1/projects/" + projectA.getId())
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should soft-delete project and mark all child endpoints deleted")
        void shouldSoftDeleteProjectSuccessfully() throws Exception {
            Project projectA = createTestProject("Deletable Project", "deletable-proj", userA);
            ApiEndpoint ep = new ApiEndpoint(projectA, "Get Health", "/health", "GET");
            apiEndpointRepository.save(ep);

            mockMvc.perform(delete("/api/v1/projects/" + projectA.getId())
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            Project deletedProj = projectRepository.findById(projectA.getId()).orElseThrow();
            assertThat(deletedProj.isDeleted()).isTrue();

            ApiEndpoint deletedEp = apiEndpointRepository.findById(ep.getId()).orElseThrow();
            assertThat(deletedEp.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("API Endpoint Registration & Validation Tests")
    class EndpointTests {

        @Test
        @DisplayName("Should register an endpoint with URL, method, headers, parameters, and masked auth")
        void shouldRegisterEndpointSuccessfully() throws Exception {
            Project project = createTestProject("E-Commerce Project", "e-commerce", userA);

            CreateEndpointRequest request = new CreateEndpointRequest(
                    "Retrieve User Orders",
                    "/api/v1/orders",
                    "GET",
                    "Fetches customer orders by status",
                    "getOrders",
                    new AuthConfigDto("BEARER", null, "Authorization", "HEADER", "secret-token-xyz-12345", null),
                    List.of(new ParameterDto("status", "query", false, "string", "Filter by status", "COMPLETED")),
                    Map.of("Accept", "application/json"),
                    null,
                    "{\"200\": {\"type\": \"array\"}}"
            );

            MvcResult result = mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/endpoints")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("Retrieve User Orders"))
                    .andExpect(jsonPath("$.data.httpMethod").value("GET"))
                    .andExpect(jsonPath("$.data.url").value("/api/v1/orders"))
                    .andExpect(jsonPath("$.data.authConfig.type").value("BEARER"))
                    .andExpect(jsonPath("$.data.authConfig.maskedValue", containsString("....")))
                    .andExpect(jsonPath("$.data.authConfig.tokenOrPassword").doesNotExist()) // Never return plaintext secret
                    .andReturn();

            assertThat(apiEndpointRepository.countByProjectIdAndDeletedAtIsNull(project.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject invalid URLs (e.g. javascript:, ftp:, malformed schemes)")
        void shouldRejectInvalidUrls() throws Exception {
            Project project = createTestProject("Security Test", "sec-test", userA);

            CreateEndpointRequest invalidUrlReq = new CreateEndpointRequest(
                    "Malicious Endpoint",
                    "javascript:alert(1)",
                    "GET",
                    null, null, null, null, null, null, null
            );

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/endpoints")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidUrlReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("url")));
        }

        @Test
        @DisplayName("Should reject invalid HTTP methods (e.g. FOOBAR)")
        void shouldRejectInvalidHttpMethods() throws Exception {
            Project project = createTestProject("Security Test", "sec-test", userA);

            CreateEndpointRequest invalidMethodReq = new CreateEndpointRequest(
                    "Bad Method Endpoint",
                    "/api/v1/test",
                    "INVALID_METHOD",
                    null, null, null, null, null, null, null
            );

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/endpoints")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidMethodReq)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("httpMethod")));
        }

        @Test
        @DisplayName("Should prevent cross-user endpoint access and modification")
        void shouldPreventCrossUserEndpointAccess() throws Exception {
            Project projectA = createTestProject("Project A", "proj-a", userA);
            ApiEndpoint endpointA = new ApiEndpoint(projectA, "Private Endpoint", "/private", "GET");
            endpointA = apiEndpointRepository.save(endpointA);

            // User B attempts to access User A's endpoint -> 403 Forbidden
            mockMvc.perform(get("/api/v1/projects/" + projectA.getId() + "/endpoints/" + endpointA.getId())
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isForbidden());

            // User B attempts to delete User A's endpoint -> 403 Forbidden
            mockMvc.perform(delete("/api/v1/projects/" + projectA.getId() + "/endpoints/" + endpointA.getId())
                            .header("Authorization", "Bearer " + tokenB))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should reject duplicate endpoint with same HTTP method and URL within project")
        void shouldRejectDuplicateEndpointRegistration() throws Exception {
            Project project = createTestProject("Unique Project", "unique-proj", userA);

            CreateEndpointRequest req1 = new CreateEndpointRequest(
                    "Endpoint 1", "/api/v1/items", "GET", null, null, null, null, null, null, null
            );
            CreateEndpointRequest req2 = new CreateEndpointRequest(
                    "Endpoint Duplicate", "/api/v1/items", "GET", null, null, null, null, null, null, null
            );

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/endpoints")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/endpoints")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.errorCode").value("DUPLICATE_RESOURCE"));
        }
    }

    @Nested
    @DisplayName("OpenAPI Specification Ingestion & Validation Tests")
    class OpenApiIngestionTests {

        private final String validYamlSpec = """
                openapi: 3.0.3
                info:
                  title: Petstore Minimal API
                  version: 1.0.0
                  description: Sample API for testing discovery
                servers:
                  - url: https://petstore.example.com/v1
                paths:
                  /pets:
                    get:
                      summary: List all pets
                      operationId: listPets
                      parameters:
                        - name: limit
                          in: query
                          description: How many items to return
                          required: false
                          schema:
                            type: integer
                      responses:
                        '200':
                          description: An array of pets
                          content:
                            application/json:
                              schema:
                                type: array
                    post:
                      summary: Create a pet
                      operationId: createPets
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                      responses:
                        '201':
                          description: Null response
                  /pets/{petId}:
                    get:
                      summary: Info for a specific pet
                      operationId: showPetById
                      parameters:
                        - name: petId
                          in: path
                          required: true
                          description: The id of the pet
                          schema:
                            type: string
                      responses:
                        '200':
                          description: Expected response to a valid request
                """;

        @Test
        @DisplayName("Should preview valid OpenAPI YAML specification with operations, schemas and parameters")
        void shouldPreviewValidOpenApiSpec() throws Exception {
            Project project = createTestProject("Petstore Project", "petstore", userA);

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/preview")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("rawSpec", validYamlSpec))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("Petstore Minimal API"))
                    .andExpect(jsonPath("$.data.totalEndpoints").value(3))
                    .andExpect(jsonPath("$.data.serverUrls[0]").value("https://petstore.example.com/v1"))
                    .andExpect(jsonPath("$.data.endpoints", hasSize(3)))
                    .andExpect(jsonPath("$.data.endpoints[*].key", containsInAnyOrder("GET:/pets", "POST:/pets", "GET:/pets/{petId}")));
        }

        @Test
        @DisplayName("Should import OpenAPI specification and persist discovered endpoints")
        void shouldImportOpenApiEndpointsSuccessfully() throws Exception {
            Project project = createTestProject("Petstore Project", "petstore", userA);

            OpenApiImportRequest request = new OpenApiImportRequest(validYamlSpec, null, false, null);

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/import")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.importedCount").value(3))
                    .andExpect(jsonPath("$.data.skippedCount").value(0))
                    .andExpect(jsonPath("$.data.overwrittenCount").value(0));

            assertThat(apiEndpointRepository.countByProjectIdAndDeletedAtIsNull(project.getId())).isEqualTo(3);
        }

        @Test
        @DisplayName("Should prevent duplicate imports by skipping existing endpoints unless overwrite is enabled")
        void shouldPreventDuplicateImportsOnReImport() throws Exception {
            Project project = createTestProject("Petstore Project", "petstore", userA);

            // First import
            OpenApiImportRequest req1 = new OpenApiImportRequest(validYamlSpec, null, false, null);
            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/import")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.importedCount").value(3));

            // Second import with overwrite = false (should skip all 3)
            OpenApiImportRequest req2 = new OpenApiImportRequest(validYamlSpec, null, false, null);
            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/import")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.importedCount").value(0))
                    .andExpect(jsonPath("$.data.skippedCount").value(3))
                    .andExpect(jsonPath("$.data.overwrittenCount").value(0));

            assertThat(apiEndpointRepository.countByProjectIdAndDeletedAtIsNull(project.getId())).isEqualTo(3);

            // Third import with overwrite = true (should overwrite all 3)
            OpenApiImportRequest req3 = new OpenApiImportRequest(validYamlSpec, null, true, null);
            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/import")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req3)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.importedCount").value(0))
                    .andExpect(jsonPath("$.data.skippedCount").value(0))
                    .andExpect(jsonPath("$.data.overwrittenCount").value(3));

            assertThat(apiEndpointRepository.countByProjectIdAndDeletedAtIsNull(project.getId())).isEqualTo(3);
        }

        @Test
        @DisplayName("Should reject malformed OpenAPI syntax with actionable error message")
        void shouldRejectMalformedOpenApiSyntax() throws Exception {
            Project project = createTestProject("Malformed Project", "malformed", userA);

            String malformedSpec = "not a valid yaml: [{unclosed_bracket: true";

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/preview")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("rawSpec", malformedSpec))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.detail", containsString("Malformed OpenAPI specification")));
        }

        @Test
        @DisplayName("Should reject empty OpenAPI specification")
        void shouldRejectEmptyOpenApiSpec() throws Exception {
            Project project = createTestProject("Empty Project", "empty-proj", userA);

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/preview")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("rawSpec", "   "))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.detail", containsString("OpenAPI specification is empty")));
        }

        @Test
        @DisplayName("Should reject specification with no paths or operations")
        void shouldRejectSpecWithNoPaths() throws Exception {
            Project project = createTestProject("No Paths Project", "no-paths", userA);

            String noPathsSpec = """
                    openapi: 3.0.3
                    info:
                      title: Empty API
                      version: 1.0.0
                    paths: {}
                    """;

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/preview")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("rawSpec", noPathsSpec))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.detail", containsString("contains no paths or operations")));
        }

        @Test
        @DisplayName("Should reject oversized OpenAPI files (> 5MB)")
        void shouldRejectOversizedOpenApiFiles() throws Exception {
            Project project = createTestProject("Oversized Project", "oversized", userA);

            // Generate string > 5MB
            byte[] oversizedBytes = new byte[6 * 1024 * 1024];
            java.util.Arrays.fill(oversizedBytes, (byte) 'a');
            String oversizedSpec = new String(oversizedBytes);

            mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/openapi/preview")
                            .header("Authorization", "Bearer " + tokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("rawSpec", oversizedSpec))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.detail", containsString("maximum allowed size of 5 MB")));
        }

        @Test
        @DisplayName("Should handle multipart file upload for preview and import")
        void shouldHandleMultipartFileUpload() throws Exception {
            Project project = createTestProject("Upload Project", "upload-proj", userA);

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "petstore.yaml",
                    "application/x-yaml",
                    validYamlSpec.getBytes()
            );

            mockMvc.perform(multipart("/api/v1/projects/" + project.getId() + "/openapi/preview/file")
                            .file(file)
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("Petstore Minimal API"))
                    .andExpect(jsonPath("$.data.totalEndpoints").value(3));

            mockMvc.perform(multipart("/api/v1/projects/" + project.getId() + "/openapi/import/file")
                            .file(file)
                            .header("Authorization", "Bearer " + tokenA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.importedCount").value(3));

            assertThat(apiEndpointRepository.countByProjectIdAndDeletedAtIsNull(project.getId())).isEqualTo(3);
        }
    }
}
