package com.sentinel.modules.testing.repository;

import com.sentinel.modules.testing.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    Optional<TestCase> findByIdAndDeletedAtIsNull(UUID id);

    List<TestCase> findAllByTestSuiteIdAndDeletedAtIsNullOrderByExecutionOrderAsc(UUID testSuiteId);

    List<TestCase> findAllByEndpointIdAndDeletedAtIsNull(UUID endpointId);
}
