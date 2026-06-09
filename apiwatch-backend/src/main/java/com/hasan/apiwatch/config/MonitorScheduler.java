package com.hasan.apiwatch.config;

import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.service.HealthCheckRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    public MonitorScheduler(
            MonitoredServiceRepository serviceRepository,
            HealthCheckRepository healthCheckRepository,
            HealthCheckRunner healthCheckRunner
    ) {
        this.serviceRepository = serviceRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.healthCheckRunner = healthCheckRunner;
    }

    @Scheduled(
            fixedDelayString = "${apiwatch.scheduler.interval-ms:5000}",
            initialDelayString = "${apiwatch.scheduler.initial-delay-ms:15000}"
    )
    public void monitorActiveServices() {
        List<MonitoredService> activeServices = serviceRepository.findAllByActiveTrueOrderByNameAsc();
        log.info("Starting scheduled monitoring run for {} services", activeServices.size());
        for (MonitoredService service : activeServices) {
            if (service.getRateLimitedUntil() != null
                    && service.getRateLimitedUntil().isAfter(Instant.now())) {
                log.info("Skipping rate-limited service {} ({}) until {}",
                        service.getName(), service.getId(), service.getRateLimitedUntil());
                continue;
            }
            if (!isDue(service, Instant.now())) {
                continue;
            }
            try {
                healthCheckRunner.run(service);
            } catch (Exception exception) {
                log.error("Monitoring failed for service {} ({})",
                        service.getName(), service.getId(), exception);
            }
        }
        log.info("Finished scheduled monitoring run");
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
