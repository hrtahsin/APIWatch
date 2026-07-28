package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.HealthCheckResponse;
import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.exception.ResourceNotFoundException;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthCheckPersistenceService {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentService incidentService;

    public HealthCheckPersistenceService(
            MonitoredServiceRepository serviceRepository,
            HealthCheckRepository healthCheckRepository,
            IncidentService incidentService
    ) {
        this.serviceRepository = serviceRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.incidentService = incidentService;
    }

    @Transactional
    public HealthCheckResponse persist(Long serviceId, HealthCheckResult result) {
        MonitoredService service = serviceRepository.findById(serviceId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Service " + serviceId + " was not found")
                );
        if (result.rateLimitedUntil() != null) {
            service.setRateLimitedUntil(result.rateLimitedUntil());
        } else if (result.clearRateLimit()) {
            service.setRateLimitedUntil(null);
        }

        HealthCheck check = new HealthCheck();
        check.setMonitoredService(service);
        check.setStatus(result.status());
        check.setHttpStatusCode(result.httpStatusCode());
        check.setResponseTimeMs(result.responseTimeMs());
        check.setFailureType(result.failureType());
        check.setErrorMessage(result.errorMessage());
        check.setRetryAfterSeconds(result.retryAfterSeconds());
        check.setRateLimitRemaining(result.rateLimitRemaining());
        check.setRateLimitResetAt(result.rateLimitResetAt());
        HealthCheck saved = healthCheckRepository.save(check);
        incidentService.evaluate(service, saved);
        return toResponse(saved);
    }

    private HealthCheckResponse toResponse(HealthCheck check) {
        return new HealthCheckResponse(
                check.getId(),
                check.getMonitoredService().getId(),
                check.getStatus(),
                check.getHttpStatusCode(),
                check.getResponseTimeMs(),
                check.getFailureType(),
                check.getErrorMessage(),
                check.getRetryAfterSeconds(),
                check.getRateLimitRemaining(),
                check.getRateLimitResetAt(),
                check.getCheckedAt()
        );
    }
}
