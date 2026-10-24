package com.sentinel.modules.testing.dto;

import com.sentinel.modules.testing.entity.TestStatus;

import java.time.Instant;
import java.util.UUID;

public class TestRunResponse {

    private UUID id;
    private UUID testSuiteId;
    private String testSuiteName;
    private UUID triggeredById;
    private String triggeredByEmail;
    private TestStatus status;
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private Long durationMs;
    private Instant startedAt;
    private Instant finishedAt;
    private Instant createdAt;

    public TestRunResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTestSuiteId() {
        return testSuiteId;
    }

    public void setTestSuiteId(UUID testSuiteId) {
        this.testSuiteId = testSuiteId;
    }

    public String getTestSuiteName() {
        return testSuiteName;
    }

    public void setTestSuiteName(String testSuiteName) {
        this.testSuiteName = testSuiteName;
    }

    public UUID getTriggeredById() {
        return triggeredById;
    }

    public void setTriggeredById(UUID triggeredById) {
        this.triggeredById = triggeredById;
    }

    public String getTriggeredByEmail() {
        return triggeredByEmail;
    }

    public void setTriggeredByEmail(String triggeredByEmail) {
        this.triggeredByEmail = triggeredByEmail;
    }

    public TestStatus getStatus() {
        return status;
    }

    public void setStatus(TestStatus status) {
        this.status = status;
    }

    public int getTotalTests() {
        return totalTests;
    }

    public void setTotalTests(int totalTests) {
        this.totalTests = totalTests;
    }

    public int getPassedTests() {
        return passedTests;
    }

    public void setPassedTests(int passedTests) {
        this.passedTests = passedTests;
    }

    public int getFailedTests() {
        return failedTests;
    }

    public void setFailedTests(int failedTests) {
        this.failedTests = failedTests;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
