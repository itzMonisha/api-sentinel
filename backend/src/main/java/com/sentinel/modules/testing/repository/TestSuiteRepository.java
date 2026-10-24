package com.sentinel.modules.testing.repository;

import com.sentinel.modules.testing.entity.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestSuiteRepository extends JpaRepository<TestSuite, UUID> {

    Optional<TestSuite> findByIdAndDeletedAtIsNull(UUID id);

    List<TestSuite> findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId);
}
