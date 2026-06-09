package com.hasan.apiwatch.config;

import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.service.HealthCheckRunner;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitorSchedulerTest {

    private final HealthCheckRepository healthCheckRepository = mock(HealthCheckRepository.class);
    private final MonitorScheduler scheduler = new MonitorScheduler(
            mock(MonitoredServiceRepository.class),
            healthCheckRepository,
            mock(HealthCheckRunner.class)
    );

    @Test
    void serviceIsDueWhenConfiguredIntervalHasElapsed() {
        Instant now = Instant.parse("2026-06-08T20:00:00Z");
        MonitoredService service = service(7L, 60);
        HealthCheck check = new HealthCheck();
        check.setCheckedAt(now.minusSeconds(61));
        when(healthCheckRepository.findTopByMonitoredServiceIdOrderByCheckedAtDesc(7L))
                .thenReturn(Optional.of(check));

        assertThat(scheduler.isDue(service, now)).isTrue();
    }

    @Test
    void serviceIsSkippedUntilConfiguredIntervalElapses() {
        Instant now = Instant.parse("2026-06-08T20:00:00Z");
        MonitoredService service = service(8L, 120);
        HealthCheck check = new HealthCheck();
        check.setCheckedAt(now.minusSeconds(30));
        when(healthCheckRepository.findTopByMonitoredServiceIdOrderByCheckedAtDesc(8L))
                .thenReturn(Optional.of(check));

        assertThat(scheduler.isDue(service, now)).isFalse();
    }

    private MonitoredService service(Long id, int intervalSeconds) {
        MonitoredService service = new MonitoredService();
        ReflectionTestUtils.setField(service, "id", id);
        service.setCheckIntervalSeconds(intervalSeconds);
        return service;
    }
}
