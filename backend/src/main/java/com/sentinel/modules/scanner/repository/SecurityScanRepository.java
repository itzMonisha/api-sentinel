package com.sentinel.modules.scanner.repository;

import com.sentinel.modules.scanner.entity.ScanStatus;
import com.sentinel.modules.scanner.entity.SecurityScan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityScanRepository extends JpaRepository<SecurityScan, UUID> {

    List<SecurityScan> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, ScanStatus status);

    long countByTriggeredByIdAndStatus(UUID userId, ScanStatus status);
}
