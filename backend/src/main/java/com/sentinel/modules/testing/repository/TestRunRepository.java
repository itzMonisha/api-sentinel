package com.sentinel.modules.testing.repository;

import com.sentinel.modules.testing.entity.TestRun;
import com.sentinel.modules.testing.entity.TestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

    List<TestRun> findAllByTestSuiteIdOrderByCreatedAtDesc(UUID testSuiteId);

    long countByTestSuiteProjectIdAndStatus(UUID projectId, TestStatus status);

    long countByTriggeredByIdAndStatus(UUID userId, TestStatus status);

    @Query("SELECT DISTINCT r.testRun FROM TestResult r WHERE r.testCase.endpoint.id = :endpointId ORDER BY r.testRun.createdAt DESC")
    List<TestRun> findDistinctTestRunsByEndpointId(@Param("endpointId") UUID endpointId);
}
