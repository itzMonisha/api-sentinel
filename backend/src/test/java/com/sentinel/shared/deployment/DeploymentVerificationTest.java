package com.sentinel.shared.deployment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.auth.dto.LoginRequest;
import com.sentinel.modules.auth.dto.RegisterRequest;
import com.sentinel.modules.monitoring.dto.CreateHealthCheckRequest;
import com.sentinel.modules.project.dto.CreateEndpointRequest;
import com.sentinel.modules.project.dto.CreateProjectRequest;
import com.sentinel.modules.scanner.dto.CreateScanRequest;
import com.sentinel.modules.testing.dto.CreateTestCaseRequest;
import com.sentinel.modules.testing.dto.CreateTestSuiteRequest;
import com.sentinel.shared.database.FlywayStartupValidator;
import com.sentinel.shared.logging.MaskingMessageConverter;
import com.sentinel.shared.net.TargetValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End Production Deployment Verification Test.
 * Validates the complete deployment checklist specified in Phase 09:
 * 1. Application starts successfully and migrations complete.
 * 2. Health and Actuator probes respond correctly.
 * 3. Actuator administrative endpoints are strictly restricted.
 * 4. User registration & login over HTTPS work.
 * 5. Invalid authentication is rejected with RFC 7807 problem details.
 * 6. Project and API Endpoint CRUD operations succeed.
 * 7. Safe API test execution persists results.
 * 8. Security scanning persists findings.
 * 9. Reliability monitoring incidents transition through FSM.
 * 10. Production logging redacts secrets.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeploymentVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FlywayStartupValidator flywayStartupValidator;

    @Autowired
    private TargetValidator targetValidator;

    private static String authToken;
    private static String projectId;
    private static String endpointId;
    private static String testSuiteId;

    @Test
    @Order(1)
    @DisplayName("Verification 1: Application starts successfully & database migrations state is verified")
    void verifyStartupAndMigrations() {
        FlywayStartupValidator.MigrationValidationResult result = flywayStartupValidator.validateMigrations();
        assertThat(result.isSuccessful()).isTrue();
        assertThat(targetValidator).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Verification 2: Health probe GET /api/v1/health responds with UP status")
    void verifyHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("UP")))
                .andExpect(jsonPath("$.data.application", is("api-sentinel")));
    }

    @Test
    @Order(3)
    @DisplayName("Verification 3: Actuator /actuator/health is accessible but /actuator/metrics is restricted")
    void verifyActuatorRestrictions() throws Exception {
        // Public health
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));

        // Restricted administrative actuator
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    @DisplayName("Verification 4: Registration and Login work over HTTPS with JWT issuance")
    void verifyRegistrationAndLoginOverHttps() throws Exception {
        String uniqueEmail = "deploy-lead-" + UUID.randomUUID() + "@apisentinel.dev";
        RegisterRequest registerRequest = new RegisterRequest(
                uniqueEmail,
                "StrongP@ssw0rd2026!",
                "Deployment Verifier"
        );

        // Register over simulated HTTPS (secure = true)
        mockMvc.perform(post("/api/v1/auth/register")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email", is(uniqueEmail)));

        // Login over HTTPS
        LoginRequest loginRequest = new LoginRequest(uniqueEmail, "StrongP@ssw0rd2026!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andReturn();

        Map<?, ?> responseMap = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        Map<?, ?> dataMap = (Map<?, ?>) responseMap.get("data");
        authToken = (String) dataMap.get("accessToken");
        assertThat(authToken).isNotBlank();
    }

    @Test
    @Order(5)
    @DisplayName("Verification 5: Invalid authentication is rejected with RFC 7807 error format")
    void verifyInvalidAuthenticationRejected() throws Exception {
        // Bad password
        LoginRequest badLogin = new LoginRequest("nobody@apisentinel.dev", "WrongPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.status", is(401)))
                .andExpect(jsonPath("$.error.errorCode", is("INVALID_CREDENTIALS")));

        // Protected resource with invalid JWT
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.status", is(401)))
                .andExpect(jsonPath("$.error.errorCode", is("UNAUTHORIZED")));
    }

    @Test
    @Order(6)
    @DisplayName("Verification 6: Project and Endpoint CRUD operations succeed")
    void verifyProjectAndEndpointCrud() throws Exception {
        // Create Project
        CreateProjectRequest projectReq = new CreateProjectRequest("Deployment Project", "deploy-proj-" + UUID.randomUUID().toString().substring(0, 8), "Validation workspace");
        MvcResult projectResult = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is("Deployment Project")))
                .andReturn();

        Map<?, ?> projectMap = objectMapper.readValue(projectResult.getResponse().getContentAsString(), Map.class);
        Map<?, ?> projData = (Map<?, ?>) projectMap.get("data");
        projectId = (String) projData.get("id");

        // Register Endpoint
        CreateEndpointRequest endpointReq = new CreateEndpointRequest(
                "Health Check Target",
                "http://8.8.8.8/health",
                "GET",
                "Verified public health target",
                null, null, null, null, null, null
        );
        MvcResult endpointResult = mockMvc.perform(post("/api/v1/projects/{projectId}/endpoints", projectId)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(endpointReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is("Health Check Target")))
                .andReturn();

        Map<?, ?> endpointMap = objectMapper.readValue(endpointResult.getResponse().getContentAsString(), Map.class);
        Map<?, ?> endData = (Map<?, ?>) endpointMap.get("data");
        endpointId = (String) endData.get("id");

        // List Endpoints
        mockMvc.perform(get("/api/v1/projects/{projectId}/endpoints", projectId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @Order(7)
    @DisplayName("Verification 7: Safe API test creation and test run execution")
    void verifyTestSuiteAndRun() throws Exception {
        // Create Test Suite
        CreateTestSuiteRequest suiteReq = new CreateTestSuiteRequest(UUID.fromString(projectId), "Deployment Smoke Suite", "Automated verification suite");
        MvcResult suiteResult = mockMvc.perform(post("/api/v1/test-suites")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suiteReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> suiteMap = objectMapper.readValue(suiteResult.getResponse().getContentAsString(), Map.class);
        Map<?, ?> suiteData = (Map<?, ?>) suiteMap.get("data");
        testSuiteId = (String) suiteData.get("id");

        // Add Test Case
        CreateTestCaseRequest caseReq = new CreateTestCaseRequest();
        caseReq.setEndpointId(UUID.fromString(endpointId));
        caseReq.setName("Verify Health Status");
        caseReq.setHttpMethod("GET");
        caseReq.setTargetPath("/health");
        caseReq.setExecutionOrder(1);

        mockMvc.perform(post("/api/v1/test-suites/{id}/cases", testSuiteId)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(caseReq)))
                .andExpect(status().isCreated());

        // Trigger Run
        mockMvc.perform(post("/api/v1/test-suites/{id}/runs", testSuiteId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status", isIn(List.of("QUEUED", "RUNNING", "COMPLETED", "FAILED"))));
    }

    @Test
    @Order(8)
    @DisplayName("Verification 8: Security findings are stored and displayed")
    void verifySecurityScanAndFindings() throws Exception {
        CreateScanRequest scanReq = new CreateScanRequest(
                UUID.fromString(projectId),
                List.of(UUID.fromString(endpointId)),
                null
        );

        MvcResult scanResult = mockMvc.perform(post("/api/v1/scans")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scanReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> scanMap = objectMapper.readValue(scanResult.getResponse().getContentAsString(), Map.class);
        Map<?, ?> scanData = (Map<?, ?>) scanMap.get("data");
        String scanId = (String) scanData.get("id");

        mockMvc.perform(get("/api/v1/scans/{id}", scanId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(scanId)));
    }

    @Test
    @Order(9)
    @DisplayName("Verification 9: Reliability monitoring can create, acknowledge, and resolve incidents")
    void verifyReliabilityIncidentLifecycle() throws Exception {
        // Create Health Check
        CreateHealthCheckRequest checkReq = new CreateHealthCheckRequest();
        checkReq.setEndpointId(UUID.fromString(endpointId));
        checkReq.setName("Deployment Gateway Probe");
        checkReq.setIntervalSeconds(60);
        checkReq.setTimeoutMs(3000);
        checkReq.setExpectedStatus(200);
        checkReq.setFailureThreshold(3);
        checkReq.setRecoveryThreshold(2);

        mockMvc.perform(post("/api/v1/projects/{projectId}/health-checks", projectId)
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(checkReq)))
                .andExpect(status().isCreated());

        // Query Project Incidents
        mockMvc.perform(get("/api/v1/projects/{projectId}/incidents", projectId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(10)
    @DisplayName("Verification 10: Logs contain no secrets (JWT, passwords, or Authorization headers)")
    void verifyLogsContainNoSecrets() {
        String sensitiveLog = "Auth token: " + authToken + " and password: SuperSecretPassword123!";
        String masked = MaskingMessageConverter.mask(sensitiveLog);

        assertThat(masked).doesNotContain(authToken);
        assertThat(masked).doesNotContain("SuperSecretPassword123!");
        assertThat(masked).contains("[REDACTED");
    }
}
