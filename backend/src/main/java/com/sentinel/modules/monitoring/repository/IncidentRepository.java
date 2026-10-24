package com.sentinel.modules.monitoring.repository;

import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.monitoring.entity.Incident;
import com.sentinel.modules.monitoring.entity.IncidentStatus;
import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.project.entity.Project;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findByProjectOrderByStartedAtDesc(Project project, Pageable pageable);

    List<Incident> findByProjectAndStatusOrderByStartedAtDesc(Project project, IncidentStatus status, Pageable pageable);

    Optional<Incident> findFirstByEndpointAndStatusInOrderByStartedAtDesc(ApiEndpoint endpoint, Collection<IncidentStatus> statuses);

    Optional<Incident> findFirstByHealthCheckAndStatusInOrderByStartedAtDesc(HealthCheck healthCheck, Collection<IncidentStatus> statuses);

    long countByProjectAndStatus(Project project, IncidentStatus status);

    long countByProject(Project project);
}
