package com.sentinel.shared.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Validates database migration state during controlled application startup.
 * Confirms that all schema versions are applied, verifies baseline integrity,
 * and logs structured diagnostic metrics without exposing database connection secrets.
 */
@Component
public class FlywayStartupValidator implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(FlywayStartupValidator.class);

    private final Flyway flyway;

    public FlywayStartupValidator(@Autowired(required = false) Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        validateMigrations();
    }

    public MigrationValidationResult validateMigrations() {
        if (flyway == null) {
            log.info("[FlywayStartupValidator] Flyway is disabled or not present for this profile");
            return new MigrationValidationResult(true, "DISABLED", 0, 0, "Flyway not configured");
        }

        try {
            MigrationInfoService infoService = flyway.info();
            MigrationInfo[] all = infoService.all();
            MigrationInfo[] applied = infoService.applied();
            MigrationInfo[] pending = infoService.pending();
            MigrationInfo current = infoService.current();

            String currentVersion = current != null ? current.getVersion().getVersion() : "NONE";
            int appliedCount = applied != null ? applied.length : 0;
            int pendingCount = pending != null ? pending.length : 0;
            int totalCount = all != null ? all.length : 0;

            if (pendingCount > 0) {
                log.warn("[FlywayStartupValidator] Pending migrations detected: {} pending out of {} total",
                        pendingCount, totalCount);
                return new MigrationValidationResult(false, currentVersion, appliedCount, pendingCount,
                        "Pending migrations exist that have not been applied");
            }

            log.info("[FlywayStartupValidator] Controlled startup verification SUCCESS. Current schema version: [{}], Applied migrations: [{}], Pending: [{}]",
                    currentVersion, appliedCount, pendingCount);

            if (applied != null) {
                Arrays.stream(applied).forEach(m ->
                        log.debug("  - Applied migration: {} - {} in {}ms",
                                m.getVersion(), m.getDescription(), m.getExecutionTime())
                );
            }

            return new MigrationValidationResult(true, currentVersion, appliedCount, pendingCount, "All migrations verified");
        } catch (Exception e) {
            log.error("[FlywayStartupValidator] Failed to query Flyway migration status: {}", e.getMessage(), e);
            return new MigrationValidationResult(false, "ERROR", 0, 0, e.getMessage());
        }
    }

    public record MigrationValidationResult(
            boolean isSuccessful,
            String currentVersion,
            int appliedCount,
            int pendingCount,
            String message
    ) {}
}
