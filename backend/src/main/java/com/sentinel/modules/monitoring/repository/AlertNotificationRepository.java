package com.sentinel.modules.monitoring.repository;

import com.sentinel.modules.monitoring.entity.AlertNotification;
import com.sentinel.modules.project.entity.Project;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertNotificationRepository extends JpaRepository<AlertNotification, UUID> {

    List<AlertNotification> findByProjectOrderBySentAtDesc(Project project, Pageable pageable);

    List<AlertNotification> findByProjectAndReadFalseOrderBySentAtDesc(Project project);

    long countByProjectAndReadFalse(Project project);
}
