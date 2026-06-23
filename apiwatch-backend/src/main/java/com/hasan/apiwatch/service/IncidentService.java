package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.IncidentResponse;
import com.hasan.apiwatch.dto.PageResponse;
import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.AuditAction;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.event.IncidentNotificationEvent;
import com.hasan.apiwatch.exception.ResourceNotFoundException;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public IncidentService(
            IncidentRepository incidentRepository,
            HealthCheckRepository healthCheckRepository,
            ApplicationEventPublisher eventPublisher,
            AuditLogService auditLogService
    ) {
        this.incidentRepository = incidentRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void evaluate(MonitoredService service, HealthCheck latestCheck) {
        if (latestCheck.getStatus() == HealthStatus.UP) {
            incidentRepository
                    .findFirstByMonitoredServiceIdAndStatus(service.getId(), IncidentStatus.ACTIVE)
                    .ifPresent(this::resolveEntity);
            return;
        }

        if (latestCheck.getStatus() != HealthStatus.DOWN) {
            return;
        }

        int threshold = service.getFailureThreshold();
        List<HealthCheck> recentChecks =
                healthCheckRepository.findByMonitoredServiceIdOrderByCheckedAtDesc(
                        service.getId(),
                        PageRequest.of(0, threshold)
                ).getContent();
        boolean thresholdReached = recentChecks.size() == threshold
                && recentChecks.stream().allMatch(check -> check.getStatus() == HealthStatus.DOWN);

        if (thresholdReached
                && incidentRepository.findFirstByMonitoredServiceIdAndStatus(
                        service.getId(),
                        IncidentStatus.ACTIVE
                ).isEmpty()) {
            Incident incident = new Incident();
            incident.setMonitoredService(service);
            incident.setStatus(IncidentStatus.ACTIVE);
            incident.setReason(threshold + " consecutive health checks failed");
            incident.setStartedAt(recentChecks.get(recentChecks.size() - 1).getCheckedAt());
            Incident saved = incidentRepository.save(incident);
            eventPublisher.publishEvent(IncidentNotificationEvent.opened(saved));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> findAll(
            IncidentStatus status,
            Long serviceId,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        );
        Page<Incident> incidents;
        if (status != null && serviceId != null) {
            incidents = incidentRepository.findByMonitoredServiceIdAndStatusOrderByStartedAtDesc(
                    serviceId,
                    status,
                    pageable
            );
        } else if (status != null) {
            incidents = incidentRepository.findByStatusOrderByStartedAtDesc(status, pageable);
        } else if (serviceId != null) {
            incidents = incidentRepository.findByMonitoredServiceIdOrderByStartedAtDesc(
                    serviceId,
                    pageable
            );
        } else {
            incidents = incidentRepository.findAll(PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    org.springframework.data.domain.Sort.by(
                            org.springframework.data.domain.Sort.Direction.DESC,
                            "startedAt"
                    )
            ));
        }
        return PageResponse.from(incidents.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public IncidentResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public IncidentResponse resolve(Long id) {
        Incident incident = getEntity(id);
        if (incident.getStatus() == IncidentStatus.ACTIVE) {
            resolveEntity(incident);
            auditLogService.record(
                    AuditAction.INCIDENT_RESOLVED,
                    "INCIDENT",
                    incident.getId(),
                    incident.getMonitoredService().getName(),
                    "Manually resolved incident for service " + incident.getMonitoredService().getId()
            );
        }
        return toResponse(incident);
    }

    private Incident getEntity(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident " + id + " was not found"));
    }

    private void resolveEntity(Incident incident) {
        Instant resolvedAt = Instant.now();
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(resolvedAt);
        incident.setDurationSeconds(Duration.between(incident.getStartedAt(), resolvedAt).toSeconds());
        Incident saved = incidentRepository.save(incident);
        eventPublisher.publishEvent(IncidentNotificationEvent.resolved(saved));
    }

    private IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getMonitoredService().getId(),
                incident.getMonitoredService().getName(),
                incident.getStatus(),
                incident.getReason(),
                incident.getStartedAt(),
                incident.getResolvedAt(),
                incident.getDurationSeconds(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
