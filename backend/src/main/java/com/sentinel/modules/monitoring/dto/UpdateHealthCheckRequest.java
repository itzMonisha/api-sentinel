package com.sentinel.modules.monitoring.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class UpdateHealthCheckRequest {

    private String name;

    @Min(value = 5, message = "Interval must be at least 5 seconds")
    @Max(value = 86400, message = "Interval cannot exceed 24 hours")
    private Integer intervalSeconds;

    @Min(value = 500, message = "Timeout must be at least 500ms")
    @Max(value = 30000, message = "Timeout cannot exceed 30000ms")
    private Integer timeoutMs;

    @Min(100)
    @Max(599)
    private Integer expectedStatus;

    @Min(1)
    @Max(20)
    private Integer failureThreshold;

    @Min(1)
    @Max(20)
    private Integer recoveryThreshold;

    private Boolean active;

    public UpdateHealthCheckRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(Integer intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Integer getExpectedStatus() {
        return expectedStatus;
    }

    public void setExpectedStatus(Integer expectedStatus) {
        this.expectedStatus = expectedStatus;
    }

    public Integer getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(Integer failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public Integer getRecoveryThreshold() {
        return recoveryThreshold;
    }

    public void setRecoveryThreshold(Integer recoveryThreshold) {
        this.recoveryThreshold = recoveryThreshold;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
