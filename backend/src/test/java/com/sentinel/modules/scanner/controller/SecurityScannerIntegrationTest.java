package com.sentinel.modules.scanner.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectMemberRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.modules.scanner.dto.CreateScanRequest;
import com.sentinel.modules.scanner.repository.SecurityFindingRepository;
import com.sentinel.modules.scanner.repository.SecurityScanRepository;
import com.sentinel.shared.security.JwtTokenProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "api-sentinel.security.allow-local-targets=true",
        "api-sentinel.outbound.read-timeout-ms=5000",
        "api-sentinel.outbound.connect-timeout-ms=2000"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityScannerIntegrationTest {

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
    private SecurityScanRepository securityScanRepository;

    @Autowired
    private SecurityFindingRepository securityFindingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private HttpServer vulnerableServer;
    private int vulnerableServerPort;
    private String serverBaseUrl;

    private User alice;
    private User bob;
    private String tokenAlice;
    private String tokenBob;
    private Project project;
    private ApiEndpoint vulnerableAdminEndpoint;
    private ApiEndpoint errorLeakEndpoint;

    @BeforeAll
    void startLocalVulnerableFixture() throws IOException {
        vulnerableServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        vulnerableServerPort = vulnerableServer.getAddress().getPort();
        serverBaseUrl = "http://127.0.0.1:" + vulnerableServerPort;

        // Route 1: Vulnerable public admin endpoint (responds 200 without auth, leaks Server header, missing HSTS & XFO)
        vulnerableServer.createContext("/vulnerable/public-admin", exchange -> {
            byte[] responseBytes = """
                    {
                      "status": "active",
                      "adminSettings": {
                        "maintenanceMode": false,
                        "systemRoles": ["SUPERADMIN"]
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Server", "Apache/2.4.49 (Debian)");
            exchange.getResponseHeaders().set("X-Powered-By", "PHP/7.4.3");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        // Route 2: Vulnerable endpoint leaking raw Java stack trace and database query syntax
        vulnerableServer.createContext("/vulnerable/error-leak", exchange -> {
            byte[] responseBytes = """
                    {
                      "error": "Internal Processing Error",
                      "details": "syntax error at or near 'SELECT * FROM users WHERE email = alice@example.com'",
                      "stackTrace": "java.lang.NullPointerException at com.sentinel.service.BillingService.process(BillingService.java:142)"
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        // Route 3: Secure hardened endpoint
        vulnerableServer.createContext("/secure/hardened", exchange -> {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer valid_")) {
                byte[] unauth = "{\"error\": \"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, unauth.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(unauth);
                }
                return;
            }

            byte[] ok = "{\"status\": \"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(ok);
            }
        });

        vulnerableServer.start();
    }

    @AfterAll
    void stopLocalVulnerableFixture() {
        if (vulnerableServer != null) {
            vulnerableServer.stop(0);
        }
    }

    @AfterEach
    void tearDownDatabase() {
        securityFindingRepository.deleteAll();
        securityScanRepository.deleteAll();
    }

    @BeforeEach
    void setUpDatabase() {
        securityFindingRepository.deleteAll();
        securityScanRepository.deleteAll();
        apiEndpointRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        alice = new User("alice@apisentinel.dev", passwordEncoder.encode("Password123!@#"), "Alice", Role.ROLE_USER);
        alice = userRepository.save(alice);
        tokenAlice = jwtTokenProvider.generateAccessToken(alice);

        bob = new User("bob@apisentinel.dev", passwordEncoder.encode("Password123!@#"), "Bob", Role.ROLE_USER);
        bob = userRepository.save(bob);
        tokenBob = jwtTokenProvider.generateAccessToken(bob);

        project = new Project("Scanner Demo Project", "scanner-demo", "Workspace for security auditing", alice);
        project.addMember(alice, ProjectRole.OWNER);
        project = projectRepository.save(project);

        vulnerableAdminEndpoint = new ApiEndpoint(
                project,
                "Admin Config Endpoint",
                serverBaseUrl + "/vulnerable/public-admin",
                "GET"
        );
        vulnerableAdminEndpoint.setDescription("Administrative system control - admin only");
        vulnerableAdminEndpoint.setAuthConfig("{\"type\":\"BEARER\"}");
        vulnerableAdminEndpoint = apiEndpointRepository.save(vulnerableAdminEndpoint);

        errorLeakEndpoint = new ApiEndpoint(
                project,
                "Error Leak Endpoint",
                serverBaseUrl + "/vulnerable/error-leak",
                "GET"
        );
        errorLeakEndpoint = apiEndpointRepository.save(errorLeakEndpoint);
    }

    @Test
    @DisplayName("Complete Phase 05 Flow: Safe Scan Execution, Finding Persistence, Sanitization, and Endpoint Audit")
    void shouldExecuteFullSecurityScanFlow() throws Exception {

        // ==========================================
        // Step 1: Launch Security Scan (POST /api/v1/projects/{projectId}/scans)
        // ==========================================
        CreateScanRequest request = new CreateScanRequest();
        request.setTestFixtureAuth(Map.of("Authorization", "Bearer low_privilege_viewer_fixture"));

        MvcResult scanLaunchResult = mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/scans")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.totalChecks", greaterThan(0)))
                .andReturn();

        String scanIdStr = objectMapper.readTree(scanLaunchResult.getResponse().getContentAsString())
                .get("data").get("id").asText();
        UUID scanId = UUID.fromString(scanIdStr);

        // ==========================================
        // Step 2: Query Scan Summary (GET /api/v1/scans/{id})
        // ==========================================
        mockMvc.perform(get("/api/v1/scans/" + scanId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(scanIdStr))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.highCount", greaterThan(0)))
                .andExpect(jsonPath("$.data.mediumCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.lowCount", greaterThan(0)));

        // ==========================================
        // Step 3: Query Granular Scan Findings (GET /api/v1/scans/{id}/findings)
        // ==========================================
        mockMvc.perform(get("/api/v1/scans/" + scanId + "/findings")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(5))))
                // Verify Unauthenticated Access finding
                .andExpect(jsonPath("$.data[*].ruleId", hasItem("PUBLIC_SENSITIVE_ENDPOINT")))
                // Verify Cleartext HTTP finding
                .andExpect(jsonPath("$.data[*].ruleId", hasItem("CLEARTEXT_HTTP_TRANSPORT")))
                // Verify Missing Headers finding
                .andExpect(jsonPath("$.data[*].ruleId", hasItem("MISSING_X_CONTENT_TYPE_OPTIONS")))
                // Verify Server Banner finding
                .andExpect(jsonPath("$.data[*].ruleId", hasItem("SERVER_BANNER_LEAKAGE")))
                // Verify Stack Trace finding
                .andExpect(jsonPath("$.data[*].ruleId", hasItem("STACK_TRACE_LEAKAGE")))
                // Verify Broken Function Level Auth finding
                .andExpect(jsonPath("$.data[*].ruleId", hasItem("BROKEN_FUNCTION_LEVEL_AUTH")));

        // ==========================================
        // Step 4: Verify Evidence Sanitization (No email/secret leaks)
        // ==========================================
        MvcResult findingsResult = mockMvc.perform(get("/api/v1/scans/" + scanId + "/findings")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andReturn();

        String findingsResponseStr = findingsResult.getResponse().getContentAsString();
        // The raw error response contained 'alice@example.com' - verify it is redacted!
        org.assertj.core.api.Assertions.assertThat(findingsResponseStr)
                .doesNotContain("alice@example.com");

        // ==========================================
        // Step 5: Query Endpoint Security Findings (GET /api/v1/endpoints/{id}/security-findings)
        // ==========================================
        mockMvc.perform(get("/api/v1/endpoints/" + vulnerableAdminEndpoint.getId() + "/security-findings")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data[0].endpointId").value(vulnerableAdminEndpoint.getId().toString()));

        // ==========================================
        // Step 6: Query Project Scan History (GET /api/v1/projects/{projectId}/scans)
        // ==========================================
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/scans")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(scanIdStr));

        // ==========================================
        // Step 7: Authorization & Cross-Tenant Isolation
        // ==========================================
        // Bob cannot launch scan on Alice's project
        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/scans")
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isForbidden());

        // Bob cannot read Alice's scan findings
        mockMvc.perform(get("/api/v1/scans/" + scanId + "/findings")
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isForbidden());
    }
}
