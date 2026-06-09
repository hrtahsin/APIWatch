package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.event.IncidentNotificationEvent;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentServiceTest {

    private IncidentRepository incidentRepository;
    private HealthCheckRepository healthCheckRepository;
    private ApplicationEventPublisher eventPublisher;
    private IncidentService incidentService;
    private MonitoredService service;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        healthCheckRepository = mock(HealthCheckRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        when(incidentRepository.save(any(Incident.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        incidentService = new IncidentService(
                incidentRepository,
                healthCheckRepository,
                eventPublisher
        );

        service = new MonitoredService();
        ReflectionTestUtils.setField(service, "id", 7L);
        service.setName("Payments");
        service.setFailureThreshold(3);
    }

    @Test
    void createsIncidentAfterConfiguredConsecutiveFailures() {
        Instant firstFailure = Instant.parse("2026-06-08T12:00:00Z");
        HealthCheck latest = check(HealthStatus.DOWN, firstFailure.plusSeconds(120));
        List<HealthCheck> recent = List.of(
                latest,
                check(HealthStatus.DOWN, firstFailure.plusSeconds(60)),
                check(HealthStatus.DOWN, firstFailure)
        );
        when(healthCheckRepository.findByMonitoredServiceIdOrderByCheckedAtDesc(
                eq(7L),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(recent));
        when(incidentRepository.findFirstByMonitoredServiceIdAndStatus(
                7L,
                IncidentStatus.ACTIVE
        )).thenReturn(Optional.empty());

        incidentService.evaluate(service, latest);

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(captor.getValue().getStartedAt()).isEqualTo(firstFailure);
        assertThat(captor.getValue().getReason()).contains("3 consecutive");
        verify(eventPublisher).publishEvent(any(IncidentNotificationEvent.class));
    }

    @Test
    void doesNotCreateIncidentBeforeThreshold() {
        HealthCheck latest = check(HealthStatus.DOWN, Instant.now());
        when(healthCheckRepository.findByMonitoredServiceIdOrderByCheckedAtDesc(
                eq(7L),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(latest, check(HealthStatus.DOWN, Instant.now()))
        ));

        incidentService.evaluate(service, latest);

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void resolvesActiveIncidentWhenServiceRecovers() {
        Incident incident = new Incident();
        incident.setMonitoredService(service);
        incident.setStatus(IncidentStatus.ACTIVE);
        incident.setStartedAt(Instant.now().minusSeconds(120));
        when(incidentRepository.findFirstByMonitoredServiceIdAndStatus(
                7L,
                IncidentStatus.ACTIVE
        )).thenReturn(Optional.of(incident));

        incidentService.evaluate(service, check(HealthStatus.UP, Instant.now()));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(incident.getDurationSeconds()).isGreaterThanOrEqualTo(120);
        verify(incidentRepository).save(incident);
        verify(eventPublisher).publishEvent(any(IncidentNotificationEvent.class));
    }

    private HealthCheck check(HealthStatus status, Instant checkedAt) {
        HealthCheck check = new HealthCheck();
        check.setMonitoredService(service);
        check.setStatus(status);
        check.setCheckedAt(checkedAt);
        return check;
    }
}
