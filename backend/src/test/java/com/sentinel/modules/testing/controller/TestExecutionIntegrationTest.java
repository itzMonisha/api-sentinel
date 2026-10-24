package com.sentinel.modules.testing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.project.dto.CreateEndpointRequest;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectMemberRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.modules.testing.dto.AssertionDefinition;
import com.sentinel.modules.testing.dto.CreateTestCaseRequest;
import com.sentinel.modules.testing.dto.CreateTestSuiteRequest;
import com.sentinel.modules.testing.dto.UpdateTestCaseRequest;
import com.sentinel.modules.testing.dto.UpdateTestSuiteRequest;
import com.sentinel.modules.testing.entity.AssertionType;
import com.sentinel.modules.scanner.repository.SecurityFindingRepository;
import com.sentinel.modules.scanner.repository.SecurityScanRepository;
import com.sentinel.modules.testing.repository.TestCaseRepository;
import com.sentinel.modules.testing.repository.TestResultRepository;
import com.sentinel.modules.testing.repository.TestRunRepository;
import com.sentinel.modules.testing.repository.TestSuiteRepository;
import com.sentinel.shared.security.JwtTokenProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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
@TestPropertySource(properties = {
        "api-sentinel.security.allow-local-targets=true",
        "api-sentinel.outbound.read-timeout-ms=5000",
        "api-sentinel.outbound.connect-timeout-ms=2000"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestExecutionIntegrationTest {

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
    private TestSuiteRepository testSuiteRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private TestResultRepository testResultRepository;

    @Autowired(required = false)
    private SecurityFindingRepository securityFindingRepository;

    @Autowired(required = false)
    private SecurityScanRepository securityScanRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private HttpServer testServer;
    private int testServerPort;
    private String serverBaseUrl;

    private User alice;
    private User bob;
    private String tokenAlice;
    private String tokenBob;
    private Project project;
    private ApiEndpoint endpoint;

    @BeforeAll
    void startLocalTestServer() throws IOException {
        testServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        testServerPort = testServer.getAddress().getPort();
        serverBaseUrl = "http://127.0.0.1:" + testServerPort;

        // 1. Endpoint: 200 OK with valid JSON & headers
        testServer.createContext("/test/200", exchange -> {
            byte[] responseBytes = """
                    {
                      "status": "SUCCESS",
                      "code": 200,
                      "data": {
                        "id": 77,
                        "role": "ADMIN",
                        "active": true
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("X-Custom-Header", "SentinelActive");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        // 2. Endpoint: 404 Not Found
        testServer.createContext("/test/404", exchange -> {
            byte[] responseBytes = """
                    {
                      "error": "Not Found",
                      "code": 404
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        // 3. Endpoint: Delayed response (~250ms)
        testServer.createContext("/test/delayed", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
            }
            byte[] responseBytes = """
                    {
                      "delayed": true,
                      "durationMs": 250
                    }
                    """.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        // 4. Endpoint: Invalid / Malformed JSON
        testServer.createContext("/test/invalid-json", exchange -> {
            byte[] responseBytes = "{ \"unclosed_bracket\": true, \"broken\": [".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        testServer.start();
    }

    @AfterAll
    void stopLocalTestServer() {
        if (testServer != null) {
            testServer.stop(0);
        }
    }

    @BeforeEach
    void setUpDatabase() {
        if (securityFindingRepository != null) {
            securityFindingRepository.deleteAll();
        }
        if (securityScanRepository != null) {
            securityScanRepository.deleteAll();
        }
        testResultRepository.deleteAll();
        testRunRepository.deleteAll();
        testCaseRepository.deleteAll();
        testSuiteRepository.deleteAll();
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

        project = new Project("Sentinel Engine Test Project", "engine-test-project", "Workspace for test engine", alice);
        project.addMember(alice, ProjectRole.OWNER);
        project = projectRepository.save(project);

        String openApiSchema = """
                {
                  "type": "object",
                  "required": ["status", "code", "data"],
                  "properties": {
                    "status": { "type": "string" },
                    "code": { "type": "integer" },
                    "data": { "type": "object" }
                  }
                }
                """;

        endpoint = new ApiEndpoint(project, "Live Success Endpoint", serverBaseUrl + "/test/200", "GET");
        endpoint.setResponseSchema(openApiSchema);
        endpoint = apiEndpointRepository.save(endpoint);
    }

    @Test
    @DisplayName("Complete Phase 04 Flow: Test Suite CRUD, Cases, Real Run Execution, Failure Detection, and Result Persistence")
    void shouldExecuteFullTestEngineLifecycle() throws Exception {

        // ==========================================
        // Step 1: Create Test Suite (POST /api/v1/test-suites)
        // ==========================================
        CreateTestSuiteRequest suiteRequest = new CreateTestSuiteRequest(
                project.getId(),
                "Integration Core Test Suite",
                "Automated suite validating 200, 404, delayed latency, and invalid JSON"
        );

        MvcResult suiteCreateResult = mockMvc.perform(post("/api/v1/test-suites")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(suiteRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("Integration Core Test Suite"))
                .andReturn();

        String suiteIdStr = objectMapper.readTree(suiteCreateResult.getResponse().getContentAsString())
                .get("data").get("id").asText();
        UUID suiteId = UUID.fromString(suiteIdStr);

        // Verify Suite GET
        mockMvc.perform(get("/api/v1/test-suites/" + suiteId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Integration Core Test Suite"));

        // ==========================================
        // Step 2: Add 5 Test Cases to Suite (POST /api/v1/test-suites/{id}/cases)
        // ==========================================

        // Case 1: All passing checks against /test/200
        CreateTestCaseRequest case1 = new CreateTestCaseRequest();
        case1.setEndpointId(endpoint.getId());
        case1.setName("Case 1: Full Valid 200 Checks");
        case1.setExecutionOrder(1);
        case1.setHttpMethod("GET");
        case1.setTargetPath("/test/200");
        case1.setAssertions(List.of(
                AssertionDefinition.of(AssertionType.STATUS_CODE_EQUALS, "200"),
                AssertionDefinition.of(AssertionType.RESPONSE_TIME_BELOW, "3000"),
                AssertionDefinition.of(AssertionType.HEADER_EXISTS, "X-Custom-Header", "Present"),
                AssertionDefinition.of(AssertionType.CONTENT_TYPE_MATCHES, "application/json"),
                AssertionDefinition.of(AssertionType.JSON_FIELD_EXISTS, "$.data.role", null),
                AssertionDefinition.of(AssertionType.JSON_FIELD_EQUALS, "$.data.role", "ADMIN"),
                AssertionDefinition.of(AssertionType.OPENAPI_SCHEMA_MATCH, null)
        ));

        mockMvc.perform(post("/api/v1/test-suites/" + suiteId + "/cases")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(case1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Case 1: Full Valid 200 Checks"));

        // Case 2: Endpoint returning 404 (Expected 200 -> FAILS)
        CreateTestCaseRequest case2 = new CreateTestCaseRequest();
        case2.setName("Case 2: Expect 200 on 404 Endpoint (Failing)");
        case2.setExecutionOrder(2);
        case2.setHttpMethod("GET");
        case2.setTargetPath(serverBaseUrl + "/test/404");
        case2.setAssertions(List.of(
                AssertionDefinition.of(AssertionType.STATUS_CODE_EQUALS, "200")
        ));

        mockMvc.perform(post("/api/v1/test-suites/" + suiteId + "/cases")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(case2)))
                .andExpect(status().isCreated());

        // Case 3: Delayed response exceeding latency threshold (Expected < 50ms, Actual ~250ms -> FAILS)
        CreateTestCaseRequest case3 = new CreateTestCaseRequest();
        case3.setName("Case 3: Latency Threshold Exceeded (Failing)");
        case3.setExecutionOrder(3);
        case3.setHttpMethod("GET");
        case3.setTargetPath(serverBaseUrl + "/test/delayed");
        case3.setAssertions(List.of(
                AssertionDefinition.of(AssertionType.STATUS_CODE_EQUALS, "200"),
                AssertionDefinition.of(AssertionType.RESPONSE_TIME_BELOW, "50")
        ));

        mockMvc.perform(post("/api/v1/test-suites/" + suiteId + "/cases")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(case3)))
                .andExpect(status().isCreated());

        // Case 4: Malformed Invalid JSON response (Field exists on corrupted JSON -> FAILS)
        CreateTestCaseRequest case4 = new CreateTestCaseRequest();
        case4.setName("Case 4: Invalid JSON Check (Failing)");
        case4.setExecutionOrder(4);
        case4.setHttpMethod("GET");
        case4.setTargetPath(serverBaseUrl + "/test/invalid-json");
        case4.setAssertions(List.of(
                AssertionDefinition.of(AssertionType.JSON_FIELD_EXISTS, "$.id", null)
        ));

        mockMvc.perform(post("/api/v1/test-suites/" + suiteId + "/cases")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(case4)))
                .andExpect(status().isCreated());

        // Case 5: Connection failure to offline port
        CreateTestCaseRequest case5 = new CreateTestCaseRequest();
        case5.setName("Case 5: Connection Failure Graceful Handling");
        case5.setExecutionOrder(5);
        case5.setHttpMethod("GET");
        case5.setTargetPath("http://127.0.0.1:59998/offline");
        case5.setAssertions(List.of(
                AssertionDefinition.of(AssertionType.STATUS_CODE_EQUALS, "200")
        ));

        mockMvc.perform(post("/api/v1/test-suites/" + suiteId + "/cases")
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(case5)))
                .andExpect(status().isCreated());

        // ==========================================
        // Step 3: Trigger Real Test Run (POST /api/v1/test-suites/{id}/runs)
        // ==========================================
        MvcResult runResult = mockMvc.perform(post("/api/v1/test-suites/" + suiteId + "/runs")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("FAILED")) // Real status reflects execution failure!
                .andExpect(jsonPath("$.data.totalTests").value(5))
                .andExpect(jsonPath("$.data.passedTests").value(1))
                .andExpect(jsonPath("$.data.failedTests").value(4))
                .andExpect(jsonPath("$.data.durationMs").isNumber())
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.finishedAt").isNotEmpty())
                .andReturn();

        String runIdStr = objectMapper.readTree(runResult.getResponse().getContentAsString())
                .get("data").get("id").asText();
        UUID runId = UUID.fromString(runIdStr);

        // ==========================================
        // Step 4: Verify Persisted Test Run Summary (GET /api/v1/test-runs/{id})
        // ==========================================
        mockMvc.perform(get("/api/v1/test-runs/" + runId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(runIdStr))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.totalTests").value(5))
                .andExpect(jsonPath("$.data.passedTests").value(1))
                .andExpect(jsonPath("$.data.failedTests").value(4));

        // ==========================================
        // Step 5: Verify Granular Persisted Results (GET /api/v1/test-runs/{id}/results)
        // ==========================================
        MvcResult resultsListResult = mockMvc.perform(get("/api/v1/test-runs/" + runId + "/results")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andReturn();

        String resultsJson = resultsListResult.getResponse().getContentAsString();

        // Check Case 1: PASSED with 7 passing checks
        mockMvc.perform(get("/api/v1/test-runs/" + runId + "/results")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(jsonPath("$.data[0].testCaseName").value("Case 1: Full Valid 200 Checks"))
                .andExpect(jsonPath("$.data[0].status").value("PASSED"))
                .andExpect(jsonPath("$.data[0].httpStatusCode").value(200))
                .andExpect(jsonPath("$.data[0].assertionResults", hasSize(7)));

        // Check Case 2: FAILED on 404
        mockMvc.perform(get("/api/v1/test-runs/" + runId + "/results")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(jsonPath("$.data[1].testCaseName").value("Case 2: Expect 200 on 404 Endpoint (Failing)"))
                .andExpect(jsonPath("$.data[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data[1].httpStatusCode").value(404))
                .andExpect(jsonPath("$.data[1].errorMessage").value(containsString("Expected HTTP status 200 but got 404")));

        // Check Case 3: FAILED on response time threshold
        mockMvc.perform(get("/api/v1/test-runs/" + runId + "/results")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(jsonPath("$.data[2].testCaseName").value("Case 3: Latency Threshold Exceeded (Failing)"))
                .andExpect(jsonPath("$.data[2].status").value("FAILED"))
                .andExpect(jsonPath("$.data[2].errorMessage").value(containsString("exceeded threshold")));

        // Check Case 4: FAILED on invalid JSON
        mockMvc.perform(get("/api/v1/test-runs/" + runId + "/results")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(jsonPath("$.data[3].testCaseName").value("Case 4: Invalid JSON Check (Failing)"))
                .andExpect(jsonPath("$.data[3].status").value("FAILED"))
                .andExpect(jsonPath("$.data[3].errorMessage").value(containsString("Response body is not valid JSON")));

        // Check Case 5: FAILED/ERROR on connection failure
        mockMvc.perform(get("/api/v1/test-runs/" + runId + "/results")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(jsonPath("$.data[4].testCaseName").value("Case 5: Connection Failure Graceful Handling"))
                .andExpect(jsonPath("$.data[4].status").value(anyOf(is("ERROR"), is("FAILED"))))
                .andExpect(jsonPath("$.data[4].errorMessage").isNotEmpty());

        // ==========================================
        // Step 6: Endpoint Historical Test Runs (GET /api/v1/endpoints/{id}/test-runs)
        // ==========================================
        mockMvc.perform(get("/api/v1/endpoints/" + endpoint.getId() + "/test-runs")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(runIdStr));

        // ==========================================
        // Step 7: Test Suite Runs History (GET /api/v1/test-suites/{id}/runs)
        // ==========================================
        mockMvc.perform(get("/api/v1/test-suites/" + suiteId + "/runs")
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(runIdStr));

        // ==========================================
        // Step 8: Update & Delete Test Case (PUT, DELETE /api/v1/test-cases/{id})
        // ==========================================
        List<com.sentinel.modules.testing.entity.TestCase> cases = testCaseRepository
                .findAllByTestSuiteIdAndDeletedAtIsNullOrderByExecutionOrderAsc(suiteId);
        assertThat(cases).hasSize(5);
        UUID case1Id = cases.get(0).getId();

        UpdateTestCaseRequest updateCaseReq = new UpdateTestCaseRequest();
        updateCaseReq.setName("Case 1 Renamed");
        mockMvc.perform(put("/api/v1/test-cases/" + case1Id)
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateCaseReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Case 1 Renamed"));

        mockMvc.perform(delete("/api/v1/test-cases/" + case1Id)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk());

        // ==========================================
        // Step 9: Update & Delete Test Suite (PUT, DELETE /api/v1/test-suites/{id})
        // ==========================================
        UpdateTestSuiteRequest updateSuiteReq = new UpdateTestSuiteRequest("Updated Suite Name", "Updated Description");
        mockMvc.perform(put("/api/v1/test-suites/" + suiteId)
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateSuiteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Suite Name"));

        mockMvc.perform(delete("/api/v1/test-suites/" + suiteId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/test-suites/" + suiteId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isNotFound());

        // ==========================================
        // Step 10: Security & BOLA Isolation
        // ==========================================
        // Bob attempts to access Alice's project test suites
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/test-suites")
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isForbidden());
    }
}
