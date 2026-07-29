package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.HealthCheckResponse;
import com.hasan.apiwatch.enums.FailureType;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OperationalMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OperationalMetrics metrics = new OperationalMetrics(
            registry,
            mock(MonitoredServiceRepository.class),
            mock(IncidentRepository.class),
            mock(NotificationDeliveryRepository.class)
    );

    @Test
    void recordsHealthChecksWithBoundedOperationalTags() {
        metrics.recordHealthCheck(new HealthCheckResponse(
                1L,
                42L,
                HealthStatus.DOWN,
                503,
                275L,
                FailureType.HTTP_STATUS,
                "Unavailable",
                null,
                null,
                null,
                Instant.now()
        ));

        assertThat(registry.get("apiwatch.health.checks")
                .tags("status", "down", "failure_type", "http_status")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get("apiwatch.health.check.duration")
                .tag("status", "down")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(275);
    }

    @Test
    void recordsSchedulerCycleOutcomes() {
        metrics.recordSchedulerDispatch(3, 2);

        assertThat(registry.get("apiwatch.scheduler.cycles").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("apiwatch.scheduler.services")
                .tag("outcome", "submitted")
                .counter()
                .count()).isEqualTo(3);
        assertThat(registry.get("apiwatch.scheduler.services")
                .tag("outcome", "skipped")
                .counter()
                .count()).isEqualTo(2);
    }
}
