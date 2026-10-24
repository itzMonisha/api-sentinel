package com.sentinel.shared.database;

import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.monitoring.entity.HealthCheckRecord;
import com.sentinel.modules.monitoring.entity.HealthCheckStatus;
import com.sentinel.modules.monitoring.entity.Incident;
import com.sentinel.modules.monitoring.entity.IncidentSeverity;
import com.sentinel.modules.monitoring.entity.IncidentStatus;
import com.sentinel.modules.monitoring.repository.HealthCheckRecordRepository;
import com.sentinel.modules.monitoring.repository.HealthCheckRepository;
import com.sentinel.modules.monitoring.repository.IncidentRepository;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import com.sentinel.modules.testing.dto.AssertionDefinition;
import com.sentinel.modules.testing.entity.AssertionType;
import com.sentinel.modules.testing.entity.TestCase;
import com.sentinel.modules.testing.entity.TestSuite;
import com.sentinel.modules.testing.repository.TestCaseRepository;
import com.sentinel.modules.testing.repository.TestSuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryJpaPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ApiEndpointRepository endpointRepository;

    @Autowired
    private TestSuiteRepository testSuiteRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private HealthCheckRepository healthCheckRepository;

    @Autowired
    private HealthCheckRecordRepository recordRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    private User sampleUser;
    private Project sampleProject;
    private ApiEndpoint sampleEndpoint;

    @BeforeEach
    void setUp() {
        sampleUser = new User("persisted.dev@apisentinel.dev", "$2a$12$samplehash", "Persisted Developer", Role.ROLE_USER);
        sampleUser = entityManager.persistAndFlush(sampleUser);

        sampleProject = new Project("Observability Core", "observability-core", "Central telemetry hub", sampleUser);
        sampleProject = entityManager.persistAndFlush(sampleProject);

        sampleEndpoint = new ApiEndpoint(sampleProject, "Metrics API", "https://api.example.com/v1/metrics", "GET");
        sampleEndpoint = entityManager.persistAndFlush(sampleEndpoint);
    }

    @Test
    @DisplayName("UserRepository: Persists entity, verifies UUID generation and email lookup")
    void testUserRepositoryPersistence() {
        Optional<User> found = userRepository.findByEmail("persisted.dev@apisentinel.dev");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getFullName()).isEqualTo("Persisted Developer");
        assertThat(userRepository.existsByEmail("persisted.dev@apisentinel.dev")).isTrue();
    }

    @Test
    @DisplayName("ProjectRepository: Query by slug and soft-delete filtering")
    void testProjectRepositorySoftDelete() {
        assertThat(projectRepository.findByIdAndDeletedAtIsNull(sampleProject.getId())).isPresent();

        sampleProject.setDeletedAt(Instant.now());
        entityManager.persistAndFlush(sampleProject);

        assertThat(projectRepository.findByIdAndDeletedAtIsNull(sampleProject.getId())).isEmpty();
    }

    @Test
    @DisplayName("ApiEndpointRepository: Pagination and HTTP method filtering")
    void testApiEndpointQueries() {
        ApiEndpoint postEp = new ApiEndpoint(sampleProject, "Create Metric", "https://api.example.com/v1/metrics", "POST");
        entityManager.persistAndFlush(postEp);

        Page<ApiEndpoint> gets = endpointRepository.findEndpointsByProject(
                sampleProject.getId(), "GET", null, PageRequest.of(0, 10)
        );
        assertThat(gets.getContent()).hasSize(1);
        assertThat(gets.getContent().get(0).getName()).isEqualTo("Metrics API");

        Page<ApiEndpoint> all = endpointRepository.findEndpointsByProject(
                sampleProject.getId(), null, "Metric", PageRequest.of(0, 10)
        );
        assertThat(all.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("TestSuiteRepository & TestCase: Cascading and execution order integrity")
    void testTestSuiteAndTestCasePersistence() {
        TestSuite suite = new TestSuite(sampleProject, "SLA Verification Suite", "Ensures response SLAs");
        suite = entityManager.persistAndFlush(suite);

        TestCase tc1 = new TestCase(suite, "Check 200 OK", "GET", "https://api.example.com/v1/metrics");
        tc1.setEndpoint(sampleEndpoint);
        tc1.setExecutionOrder(1);
        tc1.setAssertions("[{\"type\":\"STATUS_CODE_EQUALS\",\"expected\":\"200\"}]");
        entityManager.persistAndFlush(tc1);

        TestCase tc2 = new TestCase(suite, "Check Latency SLA", "GET", "https://api.example.com/v1/metrics");
        tc2.setEndpoint(sampleEndpoint);
        tc2.setExecutionOrder(2);
        tc2.setAssertions("[{\"type\":\"RESPONSE_TIME_BELOW\",\"expected\":\"1000\"}]");
        entityManager.persistAndFlush(tc2);

        List<TestCase> cases = testCaseRepository.findAllByTestSuiteIdAndDeletedAtIsNullOrderByExecutionOrderAsc(suite.getId());
        assertThat(cases).hasSize(2);
        assertThat(cases.get(0).getExecutionOrder()).isEqualTo(1);
        assertThat(cases.get(1).getExecutionOrder()).isEqualTo(2);
        assertThat(cases.get(0).getAssertions()).contains("STATUS_CODE_EQUALS");
    }

    @Test
    @DisplayName("HealthCheckRepository & HealthCheckRecord: findDueChecks and history retrieval")
    void testHealthCheckPersistenceAndDuePolling() {
        HealthCheck hc = new HealthCheck(sampleProject, sampleEndpoint, "Continuous Health Probe", 60, 5000, 200, 3, 2);
        hc.setNextCheckAt(Instant.now().minusSeconds(5));
        hc.setActive(true);
        hc = entityManager.persistAndFlush(hc);

        List<HealthCheck> due = healthCheckRepository.findDueChecks(Instant.now(), PageRequest.of(0, 10));
        assertThat(due).extracting(HealthCheck::getId).contains(hc.getId());

        HealthCheckRecord record = new HealthCheckRecord(hc, sampleEndpoint, 200, 145, true, null);
        record = entityManager.persistAndFlush(record);

        List<HealthCheckRecord> history = recordRepository.findByHealthCheckOrderByExecutedAtDesc(hc, PageRequest.of(0, 5));
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getStatusCode()).isEqualTo(200);
        assertThat(history.get(0).getResponseTimeMs()).isEqualTo(145);
        assertThat(history.get(0).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("IncidentRepository: Active incident query and deduplication state tracking")
    void testIncidentPersistence() {
        HealthCheck hc = new HealthCheck(sampleProject, sampleEndpoint, "Incident Test Probe", 60, 5000, 200, 3, 2);
        hc = entityManager.persistAndFlush(hc);

        Incident incident = new Incident(
                sampleProject, sampleEndpoint, hc,
                "Outage: Metrics API", "Service down", IncidentSeverity.CRITICAL
        );
        incident.setStatus(IncidentStatus.TRIGGERED);
        incident.setFailureCount(3);
        incident = entityManager.persistAndFlush(incident);

        Optional<Incident> open = incidentRepository.findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(
                hc, List.of(IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED)
        );
        assertThat(open).isPresent();
        assertThat(open.get().getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(open.get().getFailureCount()).isEqualTo(3);

        long activeCount = incidentRepository.countByProjectAndStatus(sampleProject, IncidentStatus.TRIGGERED);
        assertThat(activeCount).isEqualTo(1);
    }
}
