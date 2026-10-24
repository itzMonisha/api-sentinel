package com.sentinel.modules.monitoring.dto;

import com.sentinel.modules.monitoring.entity.HealthCheckStatus;

import java.util.Collections;
import java.util.List;

public class HealthMetricsDto {

    public static final int MIN_CHECKS_FOR_UPTIME = 3;

    private long totalChecks;
    private long successfulChecks;
    private long failedChecks;
    private double failureRate;
    private Double uptimePercentage;
    private boolean hasSufficientData;
    private Double avgLatencyMs;
    private Long p50LatencyMs;
    private Long p95LatencyMs;
    private Long p99LatencyMs;
    private HealthCheckStatus currentStatus;

    public HealthMetricsDto() {
    }

    public static HealthMetricsDto calculate(List<Long> latencies, long totalChecks, long successfulChecks, long failedChecks, HealthCheckStatus status) {
        HealthMetricsDto dto = new HealthMetricsDto();
        dto.totalChecks = totalChecks;
        dto.successfulChecks = successfulChecks;
        dto.failedChecks = failedChecks;
        dto.currentStatus = status != null ? status : HealthCheckStatus.UNKNOWN;

        if (totalChecks == 0) {
            dto.failureRate = 0.0;
            dto.uptimePercentage = null;
            dto.hasSufficientData = false;
            dto.avgLatencyMs = 0.0;
            dto.p50LatencyMs = 0L;
            dto.p95LatencyMs = 0L;
            dto.p99LatencyMs = 0L;
            return dto;
        }

        dto.failureRate = (double) failedChecks / totalChecks;

        if (totalChecks >= MIN_CHECKS_FOR_UPTIME) {
            dto.hasSufficientData = true;
            dto.uptimePercentage = Math.round(((double) successfulChecks / totalChecks) * 10000.0) / 100.0;
        } else {
            dto.hasSufficientData = false;
            dto.uptimePercentage = null;
        }

        if (latencies != null && !latencies.isEmpty()) {
            List<Long> sorted = new java.util.ArrayList<>(latencies);
            Collections.sort(sorted);
            double sum = 0;
            for (Long lat : sorted) {
                sum += lat;
            }
            dto.avgLatencyMs = Math.round((sum / sorted.size()) * 100.0) / 100.0;

            int n = sorted.size();
            dto.p50LatencyMs = sorted.get(Math.min((int) Math.floor(n * 0.50), n - 1));
            dto.p95LatencyMs = sorted.get(Math.min((int) Math.floor(n * 0.95), n - 1));
            dto.p99LatencyMs = sorted.get(Math.min((int) Math.floor(n * 0.99), n - 1));
        } else {
            dto.avgLatencyMs = 0.0;
            dto.p50LatencyMs = 0L;
            dto.p95LatencyMs = 0L;
            dto.p99LatencyMs = 0L;
        }

        return dto;
    }

    public long getTotalChecks() {
        return totalChecks;
    }

    public long getSuccessfulChecks() {
        return successfulChecks;
    }

    public long getFailedChecks() {
        return failedChecks;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public Double getUptimePercentage() {
        return uptimePercentage;
    }

    public boolean isHasSufficientData() {
        return hasSufficientData;
    }

    public Double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public Long getP50LatencyMs() {
        return p50LatencyMs;
    }

    public Long getP95LatencyMs() {
        return p95LatencyMs;
    }

    public Long getP99LatencyMs() {
        return p99LatencyMs;
    }

    public HealthCheckStatus getCurrentStatus() {
        return currentStatus;
    }
}
