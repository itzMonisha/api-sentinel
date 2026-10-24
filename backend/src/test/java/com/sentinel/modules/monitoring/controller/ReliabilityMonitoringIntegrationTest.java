package com.sentinel.modules.monitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.monitoring.dto.CreateHealthCheckRequest;
import com.sentinel.modules.monitoring.dto.ResolveIncidentRequest;
import com.sentinel.modules.monitoring.dto.UpdateHealthCheckRequest;
import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.monitoring.entity.Incident;
import com.sentinel.modules.monitoring.entity.IncidentStatus;
import com.sentinel.modules.monitoring.repository.AlertNotificationRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRecordRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRepository;
import com.sentinel.modules.monitoring.repository.IncidentRepository;
import com.sentinel.modules.monitoring.service.HealthCheckScheduler;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.entity.ProjectRole;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectMemberRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.modules.scanner.repository.SecurityFindingRepository;
import com.sentinel.modules.scanner.repository.SecurityScanRepository;
import com.sentinel.modules.testing.repository.TestCaseRepository;
import com.sentinel.modules.testing.repository.TestResultRepository;
import com.sentinel.modules.testing.repository.TestRunRepository;
import com.sentinel.modules.testing.repository.TestSuiteRepository;
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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
        "api-sentinel.outbound.read-timeout-ms=3000",
        "api-sentinel.outbound.connect-timeout-ms=2000",
        "api-sentinel.monitoring.scheduler-enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReliabilityMonitoringIntegrationTest {

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
    private HealthCheckRepository healthCheckRepository;

    @Autowired
    private HealthCheckRecordRepository healthCheckRecordRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private AlertNotificationRepository alertRepository;

    @Autowired(required = false)
    private SecurityFindingRepository securityFindingRepository;

    @Autowired(required = false)
    private SecurityScanRepository securityScanRepository;

    @Autowired(required = false)
    private TestResultRepository testResultRepository;

    @Autowired(required = false)
    private TestRunRepository testRunRepository;

    @Autowired(required = false)
    private TestCaseRepository testCaseRepository;

    @Autowired(required = false)
    private TestSuiteRepository testSuiteRepository;

    @Autowired
    private HealthCheckScheduler healthCheckScheduler;

    @Autowired
    private com.sentinel.modules.monitoring.service.ReliabilityMonitoringService reliabilityMonitoringService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private HttpServer dynamicServer;
    private int dynamicServerPort;
    private String dynamicServerBaseUrl;
    private final AtomicBoolean isServerHealthy = new AtomicBoolean(true);

    private User alice;
    private User bob;
    private String tokenAlice;
    private String tokenBob;
    private Project project;
    private ApiEndpoint monitoredEndpoint;

    @BeforeAll
    void startDynamicLocalFixture() throws IOException {
        dynamicServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        dynamicServerPort = dynamicServer.getAddress().getPort();
        dynamicServerBaseUrl = "http://127.0.0.1:" + dynamicServerPort;

        dynamicServer.createContext("/dynamic-probe", exchange -> {
            if (isServerHealthy.get()) {
                byte[] okBytes = "{\"status\": \"HEALTHY\", \"service\": \"payments\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, okBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(okBytes);
                }
            } else {
                byte[] failBytes = "{\"error\": \"Payment gateway upstream failure\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, failBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(failBytes);
                }
            }
        });

        dynamicServer.start();
    }

    @AfterAll
    void stopDynamicLocalFixture() {
        if (dynamicServer != null) {
            dynamicServer.stop(0);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDatabase();

        alice = new User("alice.reliability@apisentinel.dev", passwordEncoder.encode("Password123!@#"), "Alice SRE", Role.ROLE_USER);
        alice = userRepository.save(alice);
        tokenAlice = jwtTokenProvider.generateAccessToken(alice);

        bob = new User("bob.unauthorized@apisentinel.dev", passwordEncoder.encode("Password123!@#"), "Bob External", Role.ROLE_USER);
        bob = userRepository.save(bob);
        tokenBob = jwtTokenProvider.generateAccessToken(bob);

        project = new Project("Payments Core", "payments-core", "Workspace for payments infrastructure", alice);
        project.addMember(alice, ProjectRole.OWNER);
        project = projectRepository.save(project);

        monitoredEndpoint = new ApiEndpoint(
                project,
                "Dynamic Payments Probe",
                dynamicServerBaseUrl + "/dynamic-probe",
                "GET"
        );
        monitoredEndpoint = apiEndpointRepository.save(monitoredEndpoint);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        alertRepository.deleteAll();
        incidentRepository.deleteAll();
        healthCheckRecordRepository.deleteAll();
        healthCheckRepository.deleteAll();
        if (securityFindingRepository != null) securityFindingRepository.deleteAll();
        if (securityScanRepository != null) securityScanRepository.deleteAll();
        if (testResultRepository != null) testResultRepository.deleteAll();
        if (testRunRepository != null) testRunRepository.deleteAll();
        if (testCaseRepository != null) testCaseRepository.deleteAll();
        if (testSuiteRepository != null) testSuiteRepository.deleteAll();
        apiEndpointRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("End-to-End: Failing endpoint triggers Incident, deduplicates ongoing outage, and auto-recovers when healthy")
    void shouldExecuteFullReliabilityMonitoringLifecycle() throws Exception {
        // 1. Configure Health Check for dynamic probe endpoint: interval=10s, failureThreshold=3, recoveryThreshold=2
        CreateHealthCheckRequest createReq = new CreateHealthCheckRequest();
        createReq.setEndpointId(monitoredEndpoint.getId());
        createReq.setName("Payment Dynamic Health Check");
        createReq.setIntervalSeconds(10);
        createReq.setTimeoutMs(3000);
        createReq.setExpectedStatus(200);
        createReq.setFailureThreshold(3);
        createReq.setRecoveryThreshold(2);
        createReq.setActive(true);

        MvcResult createResult = mockMvc.perform(post("/api/v1/projects/{projectId}/health-checks", project.getId())
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Payment Dynamic Health Check"))
                .andExpect(jsonPath("$.data.failureThreshold").value(3))
                .andExpect(jsonPath("$.data.recoveryThreshold").value(2))
                .andExpect(jsonPath("$.data.lastStatus").value("UNKNOWN"))
                .andReturn();

        String checkIdStr = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asText();
        UUID checkId = UUID.fromString(checkIdStr);

        // 2. Set server to failing state (500 Internal Server Error)
        isServerHealthy.set(false);

        // Probe 1: Failure 1 -> DEGRADED, no incident yet
        mockMvc.perform(post("/api/v1/health-checks/{id}/trigger", checkId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.statusCode").value(500));

        HealthCheck checkAfter1 = healthCheckRepository.findById(checkId).orElseThrow();
        assertThat(checkAfter1.getConsecutiveFailures()).isEqualTo(1);
        assertThat(checkAfter1.getLastStatus().name()).isEqualTo("DEGRADED");
        assertThat(incidentRepository.count()).isEqualTo(0);

        // Probe 2: Failure 2 -> DEGRADED, no incident yet
        mockMvc.perform(post("/api/v1/health-checks/{id}/trigger", checkId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk());

        HealthCheck checkAfter2 = healthCheckRepository.findById(checkId).orElseThrow();
        assertThat(checkAfter2.getConsecutiveFailures()).isEqualTo(2);
        assertThat(checkAfter2.getLastStatus().name()).isEqualTo("DEGRADED");
        assertThat(incidentRepository.count()).isEqualTo(0);

        // Probe 3: Failure 3 (Threshold crossed!) -> DOWN, INCIDENT CREATED!
        mockMvc.perform(post("/api/v1/health-checks/{id}/trigger", checkId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk());

        HealthCheck checkAfter3 = healthCheckRepository.findById(checkId).orElseThrow();
        assertThat(checkAfter3.getConsecutiveFailures()).isEqualTo(3);
        assertThat(checkAfter3.getLastStatus().name()).isEqualTo("DOWN");

        // Verify Incident was created
        assertThat(incidentRepository.count()).isEqualTo(1);
        Incident incident = incidentRepository.findAll().get(0);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.TRIGGERED);
        assertThat(incident.getFailureCount()).isEqualTo(3);
        assertThat(incident.getTitle()).contains("Outage");

        // Verify In-App Alert was delivered
        assertThat(alertRepository.count()).isGreaterThanOrEqualTo(1);

        // 3. Probe 4: Ongoing outage (DEDUPLICATION TEST)
        mockMvc.perform(post("/api/v1/health-checks/{id}/trigger", checkId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk());

        // Verify deduplication: Still exactly 1 incident record exists in DB!
        assertThat(incidentRepository.count()).isEqualTo(1);
        Incident dedupIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(dedupIncident.getFailureCount()).isEqualTo(4);

        // 4. Recovery: Toggle local test endpoint back to healthy (200 OK)!
        isServerHealthy.set(true);

        // Probe 5: Success 1 -> Consecutive successes = 1. Incident remains TRIGGERED
        mockMvc.perform(post("/api/v1/health-checks/{id}/trigger", checkId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.statusCode").value(200));

        HealthCheck checkAfterRec1 = healthCheckRepository.findById(checkId).orElseThrow();
        assertThat(checkAfterRec1.getConsecutiveSuccesses()).isEqualTo(1);
        assertThat(checkAfterRec1.getConsecutiveFailures()).isEqualTo(0);
        Incident incidentDuringRec1 = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(incidentDuringRec1.getStatus()).isEqualTo(IncidentStatus.TRIGGERED);

        // Probe 6: Success 2 (Recovery threshold = 2 met!) -> Incident AUTO-RESOLVED!
        mockMvc.perform(post("/api/v1/health-checks/{id}/trigger", checkId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));

        HealthCheck checkAfterRec2 = healthCheckRepository.findById(checkId).orElseThrow();
        assertThat(checkAfterRec2.getConsecutiveSuccesses()).isEqualTo(2);
        assertThat(checkAfterRec2.getLastStatus().name()).isEqualTo("HEALTHY");

        Incident resolvedIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(resolvedIncident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(resolvedIncident.getResolvedAt()).isNotNull();
        assertThat(resolvedIncident.getRootCause()).contains("automatically recovered");

        // 5. Query incident list endpoint
        mockMvc.perform(get("/api/v1/projects/{projectId}/incidents", project.getId())
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.data[0].failureCount").value(4));

        // 6. Query health check telemetry metrics
        mockMvc.perform(get("/api/v1/health-checks/{id}", checkId)
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.totalChecks").value(6))
                .andExpect(jsonPath("$.data.metrics.successfulChecks").value(2))
                .andExpect(jsonPath("$.data.metrics.failedChecks").value(4))
                .andExpect(jsonPath("$.data.metrics.hasSufficientData").value(true))
                .andExpect(jsonPath("$.data.metrics.uptimePercentage").value(33.33));

        // 7. Query Dashboard Summary
        mockMvc.perform(get("/api/v1/projects/{projectId}/dashboard/summary", project.getId())
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMonitoredEndpoints").value(1))
                .andExpect(jsonPath("$.data.healthyEndpoints").value(1))
                .andExpect(jsonPath("$.data.activeIncidentsCount").value(0))
                .andExpect(jsonPath("$.data.totalIncidents").value(1));
    }

    @Test
    @DisplayName("Incident Management: Acknowledging and manually resolving an incident")
    void shouldAcknowledgeAndResolveIncidentManually() throws Exception {
        // Create an incident directly
        Incident incident = new Incident(
                project,
                monitoredEndpoint,
                null,
                "API Outage: Checkout Service",
                "High latency and 504 timeouts",
                com.sentinel.modules.monitoring.entity.IncidentSeverity.CRITICAL
        );
        incident = incidentRepository.save(incident);

        // 1. Acknowledge incident
        mockMvc.perform(post("/api/v1/incidents/{id}/acknowledge", incident.getId())
                        .header("Authorization", "Bearer " + tokenAlice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.data.acknowledgedByName").value("Alice SRE"))
                .andExpect(jsonPath("$.data.acknowledgedAt").isNotEmpty());

        // 2. Resolve incident with root cause
        ResolveIncidentRequest resolveReq = new ResolveIncidentRequest("Upstream database connection pool exhausted. Pool size increased from 20 to 100.");
        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", incident.getId())
                        .header("Authorization", "Bearer " + tokenAlice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedByName").value("Alice SRE"))
                .andExpect(jsonPath("$.data.rootCause").value(resolveReq.getRootCause()));
    }

    @Test
    @DisplayName("Authorization: Denies non-member access to monitoring resources")
    void shouldDenyAccessToNonMembers() throws Exception {
        // Bob tries to create health check in Alice's project
        CreateHealthCheckRequest req = new CreateHealthCheckRequest();
        req.setEndpointId(monitoredEndpoint.getId());
        req.setName("Malicious Probe");

        mockMvc.perform(post("/api/v1/projects/{projectId}/health-checks", project.getId())
                        .header("Authorization", "Bearer " + tokenBob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // Bob tries to read incidents
        mockMvc.perform(get("/api/v1/projects/{projectId}/incidents", project.getId())
                        .header("Authorization", "Bearer " + tokenBob))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Scheduler: Bounded polling executes due health checks and persists records")
    void shouldExecuteDueHealthChecksViaScheduler() {
        HealthCheck check = new HealthCheck(project, monitoredEndpoint, "Scheduled Probe", 5, 2000, 200, 3, 2);
        check.setNextCheckAt(Instant.now().minusSeconds(10)); // Due for check
        check = healthCheckRepository.save(check);

        isServerHealthy.set(true);

        // Run scheduler poll cycle with active instance
        HealthCheckScheduler activeScheduler = new HealthCheckScheduler(healthCheckRepository, reliabilityMonitoringService, true, 4);
        try {
            activeScheduler.pollAndExecuteDueHealthChecks();

            // Allow worker thread brief moment to complete probe
            Thread.sleep(600);

            // Verify nextCheckAt was advanced
            HealthCheck updatedCheck = healthCheckRepository.findById(check.getId()).orElseThrow();
            assertThat(updatedCheck.getNextCheckAt()).isAfter(Instant.now().minusSeconds(1));

            // Verify record was persisted
            assertThat(healthCheckRecordRepository.count()).isGreaterThanOrEqualTo(1);
        } catch (InterruptedException ignored) {
        } finally {
            activeScheduler.shutdown();
        }
    }
}
