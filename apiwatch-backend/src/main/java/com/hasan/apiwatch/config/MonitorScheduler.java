package com.hasan.apiwatch.config;

import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.service.HealthCheckRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.Instant;

@Component
@ConditionalOnProperty(
        name = "apiwatch.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRunner healthCheckRunner;

    public MonitorScheduler(
            MonitoredServiceRepository serviceRepository,
            HealthCheckRunner healthCheckRunner
    ) {
        this.serviceRepository = serviceRepository;
        this.healthCheckRunner = healthCheckRunner;
    }

    @Scheduled(
            fixedDelayString = "${apiwatch.scheduler.interval-ms:60000}",
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
            try {
                healthCheckRunner.run(service);
            } catch (Exception exception) {
                log.error("Monitoring failed for service {} ({})",
                        service.getName(), service.getId(), exception);
            }
        }
        log.info("Finished scheduled monitoring run");
    }
}
