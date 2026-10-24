package com.sentinel.modules.monitoring.service;

import com.sentinel.modules.monitoring.entity.HealthCheck;
import com.sentinel.modules.monitoring.repository.HealthCheckRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class HealthCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckScheduler.class);

    private final HealthCheckRepository healthCheckRepository;
    private final ReliabilityMonitoringService monitoringService;
    private final ExecutorService probeExecutor;
    private final boolean schedulerEnabled;

    public HealthCheckScheduler(
            HealthCheckRepository healthCheckRepository,
            ReliabilityMonitoringService monitoringService,
            @Value("${api-sentinel.monitoring.scheduler-enabled:true}") boolean schedulerEnabled,
            @Value("${api-sentinel.monitoring.worker-threads:8}") int workerThreads) {
        this.healthCheckRepository = healthCheckRepository;
        this.monitoringService = monitoringService;
        this.schedulerEnabled = schedulerEnabled;

        int threads = Math.max(workerThreads, 2);
        this.probeExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "SentinelHealthProbeWorker");
            t.setDaemon(true);
            return t;
        });
        log.info("Initialized HealthCheckScheduler with bounded pool of {} worker threads (enabled: {})", threads, schedulerEnabled);
    }

    @Scheduled(fixedDelayString = "${api-sentinel.monitoring.poll-interval-ms:5000}")
    public void pollAndExecuteDueHealthChecks() {
        if (!schedulerEnabled) {
            return;
        }

        try {
            Instant now = Instant.now();
            List<HealthCheck> dueChecks = healthCheckRepository.findDueChecks(now, PageRequest.of(0, 50));

            if (dueChecks.isEmpty()) {
                return;
            }

            log.debug("Found {} health checks due for polling", dueChecks.size());

            for (HealthCheck check : dueChecks) {
                UUID checkId = check.getId();
                // Defer nextCheckAt immediately to prevent duplicate pick up in case of long probe execution
                check.setNextCheckAt(now.plusSeconds(check.getIntervalSeconds()));
                healthCheckRepository.save(check);

                // Dispatch into bounded thread pool
                probeExecutor.submit(() -> {
                    try {
                        monitoringService.executeProbeById(checkId);
                    } catch (Exception e) {
                        log.error("Error executing background probe for check '{}': {}", checkId, e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.error("Unexpected error in HealthCheckScheduler poll cycle: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down HealthCheckScheduler worker pool...");
        probeExecutor.shutdown();
        try {
            if (!probeExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                probeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            probeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
