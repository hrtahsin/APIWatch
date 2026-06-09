package com.hasan.apiwatch.config;

import com.hasan.apiwatch.service.HistoryRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(
        name = "apiwatch.retention.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final HistoryRetentionService retentionService;

    public RetentionScheduler(HistoryRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${apiwatch.retention.cron:0 30 2 * * *}")
    public void cleanupHistory() {
        HistoryRetentionService.CleanupResult result = retentionService.cleanup(Instant.now());
        log.info(
                "History cleanup deleted {} checks, {} incidents, and {} notification deliveries",
                result.healthChecks(),
                result.incidents(),
                result.notificationDeliveries()
        );
    }
}
