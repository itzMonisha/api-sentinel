package com.sentinel.shared.database;

import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import com.sentinel.modules.project.repository.ApiEndpointRepository;
import com.sentinel.modules.project.repository.ProjectRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class PostgreSqlContainerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sentinel_testcontainers")
            .withUsername("test_user")
            .withPassword("test_password");

    @BeforeAll
    static void checkDocker() {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker daemon is not reachable on this host; skipping live Testcontainers execution."
        );
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        }
    }

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private ProjectRepository projectRepository;

    @Autowired(required = false)
    private ApiEndpointRepository endpointRepository;

    @Test
    @DisplayName("Verify real PostgreSQL persistence when Docker container is active")
    void shouldPersistAndQueryPostgreSql() {
        Assumptions.assumeTrue(postgres.isRunning(), "PostgreSQL container is not active");

        User user = new User("pg.test@apisentinel.dev", "hash", "PG Test User", Role.ROLE_USER);
        user = userRepository.save(user);
        assertThat(user.getId()).isNotNull();

        Project project = new Project("PG Container Project", "pg-container-project", "Container persistence", user);
        project = projectRepository.save(project);
        assertThat(project.getId()).isNotNull();

        ApiEndpoint endpoint = new ApiEndpoint(project, "Health Endpoint", "https://api.example.com/health", "GET");
        endpoint = endpointRepository.save(endpoint);
        assertThat(endpoint.getId()).isNotNull();

        assertThat(endpointRepository.findById(endpoint.getId())).isPresent();
    }
}
