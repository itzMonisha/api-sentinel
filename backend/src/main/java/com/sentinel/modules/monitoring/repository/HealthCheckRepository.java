package com.sentinel.modules.monitoring.repository;

import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.project.entity.Project;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HealthCheckRepository extends JpaRepository<HealthCheck, UUID> {

    List<HealthCheck> findByProject(Project project);

    List<HealthCheck> findByEndpointId(UUID endpointId);

    Optional<HealthCheck> findFirstByEndpointId(UUID endpointId);

    long countByProject(Project project);

    @Query("SELECT h FROM HealthCheck h JOIN FETCH h.endpoint JOIN FETCH h.project WHERE h.active = true AND h.nextCheckAt <= :now ORDER BY h.nextCheckAt ASC")
    List<HealthCheck> findDueChecks(@Param("now") Instant now, Pageable pageable);
}
