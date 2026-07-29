package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.HealthCheckResponse;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class OperationalMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter schedulerCycles;
    private final Counter schedulerSubmitted;
    private final Counter schedulerSkipped;

    public OperationalMetrics(
            MeterRegistry meterRegistry,
            MonitoredServiceRepository serviceRepository,
            IncidentRepository incidentRepository,
            NotificationDeliveryRepository deliveryRepository
    ) {
        this.meterRegistry = meterRegistry;
        this.schedulerCycles = Counter.builder("apiwatch.scheduler.cycles")
                .description("Number of scheduler dispatch cycles")
                .register(meterRegistry);
        this.schedulerSubmitted = schedulerCounter(meterRegistry, "submitted");
        this.schedulerSkipped = schedulerCounter(meterRegistry, "skipped");

        Gauge.builder(
                        "apiwatch.services.active",
                        serviceRepository,
                        repository -> repository.countByActiveTrue()
                )
                .description("Number of actively monitored services")
                .register(meterRegistry);
        Gauge.builder(
                        "apiwatch.incidents.active",
                        incidentRepository,
                        repository -> repository.countByStatus(IncidentStatus.ACTIVE)
                )
                .description("Number of unresolved incidents")
                .register(meterRegistry);
        registerDeliveryGauge(
                meterRegistry,
                deliveryRepository,
                NotificationDeliveryStatus.PENDING
        );
        registerDeliveryGauge(
                meterRegistry,
                deliveryRepository,
                NotificationDeliveryStatus.PROCESSING
        );
        registerDeliveryGauge(
                meterRegistry,
                deliveryRepository,
                NotificationDeliveryStatus.FAILED
        );
    }

    public void recordHealthCheck(HealthCheckResponse response) {
        String status = response.status().name().toLowerCase();
        String failureType = response.failureType() == null
                ? "none"
                : response.failureType().name().toLowerCase();
        Counter.builder("apiwatch.health.checks")
                .description("Completed API health checks")
                .tag("status", status)
                .tag("failure_type", failureType)
                .register(meterRegistry)
                .increment();
        Timer.builder("apiwatch.health.check.duration")
                .description("Observed API health-check response time")
                .tag("status", status)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofMillis(response.responseTimeMs()));
    }

    public void recordSchedulerDispatch(int submitted, int skipped) {
        schedulerCycles.increment();
        schedulerSubmitted.increment(submitted);
        schedulerSkipped.increment(skipped);
    }

    private Counter schedulerCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("apiwatch.scheduler.services")
                .description("Services considered during scheduler dispatch")
                .tag("outcome", outcome)
                .register(registry);
    }

    private void registerDeliveryGauge(
            MeterRegistry registry,
            NotificationDeliveryRepository repository,
            NotificationDeliveryStatus status
    ) {
        Gauge.builder(
                        "apiwatch.notifications.deliveries",
                        repository,
                        value -> value.countByStatus(status)
                )
                .description("Notification deliveries by operational state")
                .tag("status", status.name().toLowerCase())
                .register(registry);
    }
}
