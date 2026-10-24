package com.sentinel.modules.scanner.repository;

import com.sentinel.modules.scanner.entity.SecurityFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityFindingRepository extends JpaRepository<SecurityFinding, UUID> {

    List<SecurityFinding> findAllBySecurityScanIdOrderByCreatedAtAsc(UUID securityScanId);

    List<SecurityFinding> findAllByEndpointIdOrderByCreatedAtDesc(UUID endpointId);
}
