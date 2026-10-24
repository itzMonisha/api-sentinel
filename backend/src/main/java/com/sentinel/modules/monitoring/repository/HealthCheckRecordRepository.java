package com.sentinel.modules.monitoring.repository;

import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.monitoring.entity.HealthCheckRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface HealthCheckRecordRepository extends JpaRepository<HealthCheckRecord, UUID> {

    List<HealthCheckRecord> findByHealthCheckOrderByExecutedAtDesc(HealthCheck healthCheck, Pageable pageable);

    List<HealthCheckRecord> findByEndpointIdOrderByExecutedAtDesc(UUID endpointId, Pageable pageable);

    List<HealthCheckRecord> findByHealthCheckAndExecutedAtAfterOrderByExecutedAtDesc(HealthCheck healthCheck, Instant since);

    long countByHealthCheck(HealthCheck healthCheck);

    @Query("SELECT r FROM HealthCheckRecord r WHERE r.healthCheck = :healthCheck ORDER BY r.executedAt DESC")
    List<HealthCheckRecord> findRecentRecords(@Param("healthCheck") HealthCheck healthCheck, Pageable pageable);
}
