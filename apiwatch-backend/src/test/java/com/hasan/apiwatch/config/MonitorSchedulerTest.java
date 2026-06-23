package com.hasan.apiwatch.config;

import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.service.HealthCheckRunner;
import com.hasan.apiwatch.service.MonitoringLeaseService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitorSchedulerTest {

    private final MonitoredServiceRepository serviceRepository = mock(MonitoredServiceRepository.class);
    private final HealthCheckRepository healthCheckRepository = mock(HealthCheckRepository.class);
    private final HealthCheckRunner healthCheckRunner = mock(HealthCheckRunner.class);
    private final MonitoringLeaseService monitoringLeaseService = mock(MonitoringLeaseService.class);
    private final MonitorScheduler scheduler = new MonitorScheduler(
            serviceRepository,
            healthCheckRepository,
            healthCheckRunner,
            monitoringLeaseService,
            Runnable::run
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

    @Test
    void dispatchesDueServiceThroughLeaseAndReleasesAfterCheck() {
        MonitoredService service = service(9L, 60);
        when(serviceRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(service));
        when(healthCheckRepository.findTopByMonitoredServiceIdOrderByCheckedAtDesc(9L))
                .thenReturn(Optional.empty());
        when(monitoringLeaseService.tryAcquire(9L)).thenReturn(true);

        scheduler.monitorActiveServices();

        verify(healthCheckRunner).run(service);
        verify(monitoringLeaseService).release(9L);
    }

    @Test
    void skipsDueServiceWhenLeaseIsAlreadyHeld() {
        MonitoredService service = service(10L, 60);
        when(serviceRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(service));
        when(healthCheckRepository.findTopByMonitoredServiceIdOrderByCheckedAtDesc(10L))
                .thenReturn(Optional.empty());
        when(monitoringLeaseService.tryAcquire(10L)).thenReturn(false);

        scheduler.monitorActiveServices();

        verify(healthCheckRunner, never()).run(service);
        verify(monitoringLeaseService, never()).release(10L);
    }

    @Test
    void releasesLeaseWhenWorkerQueueRejectsTask() {
        MonitorScheduler rejectingScheduler = new MonitorScheduler(
                serviceRepository,
                healthCheckRepository,
                healthCheckRunner,
                monitoringLeaseService,
                task -> {
                    throw new TaskRejectedException("queue full");
                }
        );
        MonitoredService service = service(11L, 60);
        when(serviceRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(service));
        when(healthCheckRepository.findTopByMonitoredServiceIdOrderByCheckedAtDesc(11L))
                .thenReturn(Optional.empty());
        when(monitoringLeaseService.tryAcquire(11L)).thenReturn(true);

        rejectingScheduler.monitorActiveServices();

        verify(healthCheckRunner, never()).run(service);
        verify(monitoringLeaseService).release(11L);
    }

    private MonitoredService service(Long id, int intervalSeconds) {
        MonitoredService service = new MonitoredService();
        ReflectionTestUtils.setField(service, "id", id);
        service.setName("Service " + id);
        service.setCheckIntervalSeconds(intervalSeconds);
        return service;
    }
}
