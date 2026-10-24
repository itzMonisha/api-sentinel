package com.sentinel.modules.testing.repository;

import com.sentinel.modules.testing.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    List<TestResult> findAllByTestRunIdOrderByCreatedAtAsc(UUID testRunId);

    List<TestResult> findAllByTestCaseEndpointIdOrderByCreatedAtDesc(UUID endpointId);
}
