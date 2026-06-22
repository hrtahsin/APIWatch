package com.hasan.apiwatch.config;

import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.service.HealthCheckRunner;
import com.hasan.apiwatch.service.MonitoringLeaseService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "apiwatch.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final HealthCheckRunner healthCheckRunner;
    private final MonitoringLeaseService monitoringLeaseService;
    private final TaskExecutor monitoringTaskExecutor;

    public MonitorScheduler(
            MonitoredServiceRepository serviceRepository,
            HealthCheckRepository healthCheckRepository,
            HealthCheckRunner healthCheckRunner,
            MonitoringLeaseService monitoringLeaseService,
            @Qualifier("monitoringTaskExecutor") TaskExecutor monitoringTaskExecutor
    ) {
        this.serviceRepository = serviceRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.healthCheckRunner = healthCheckRunner;
        this.monitoringLeaseService = monitoringLeaseService;
        this.monitoringTaskExecutor = monitoringTaskExecutor;
    }

    @Scheduled(
            fixedDelayString = "${apiwatch.scheduler.interval-ms:5000}",
            initialDelayString = "${apiwatch.scheduler.initial-delay-ms:15000}"
    )
    public void monitorActiveServices() {
        List<MonitoredService> activeServices = serviceRepository.findAllByActiveTrueOrderByNameAsc();
        Instant now = Instant.now();
        int submitted = 0;
        int skipped = 0;
        log.info("Starting scheduled monitoring dispatch for {} services", activeServices.size());
        for (MonitoredService service : activeServices) {
            if (service.getRateLimitedUntil() != null
                    && service.getRateLimitedUntil().isAfter(now)) {
                log.info("Skipping rate-limited service {} ({}) until {}",
                        service.getName(), service.getId(), service.getRateLimitedUntil());
                skipped++;
                continue;
            }
            if (!isDue(service, now)) {
                skipped++;
                continue;
            }

            if (!tryAcquireLease(service)) {
                log.info("Skipping service {} ({}) because no monitoring lease is available",
                        service.getName(), service.getId());
                skipped++;
                continue;
            }

            try {
                monitoringTaskExecutor.execute(() -> runLeasedCheck(service));
                submitted++;
            } catch (TaskRejectedException exception) {
                monitoringLeaseService.release(service.getId());
                skipped++;
                log.warn("Monitoring queue rejected service {} ({})",
                        service.getName(), service.getId(), exception);
            }
        }
        log.info("Finished scheduled monitoring dispatch: submitted={}, skipped={}",
                submitted, skipped);
    }

    private boolean tryAcquireLease(MonitoredService service) {
        try {
            return monitoringLeaseService.tryAcquire(service.getId());
        } catch (Exception exception) {
            log.warn("Could not acquire monitoring lease for service {} ({})",
                    service.getName(), service.getId(), exception);
            return false;
        }
    }

    private void runLeasedCheck(MonitoredService service) {
        try {
            healthCheckRunner.run(service);
        } catch (Exception exception) {
            log.error("Monitoring failed for service {} ({})",
                    service.getName(), service.getId(), exception);
        } finally {
            monitoringLeaseService.release(service.getId());
        }
    }

    boolean isDue(MonitoredService service, Instant now) {
        return healthCheckRepository
                .findTopByMonitoredServiceIdOrderByCheckedAtDesc(service.getId())
                .map(check -> !check.getCheckedAt()
                        .plus(Duration.ofSeconds(service.getCheckIntervalSeconds()))
                        .isAfter(now))
                .orElse(true);
    }
}
