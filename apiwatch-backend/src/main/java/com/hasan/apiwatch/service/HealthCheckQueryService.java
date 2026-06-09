package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.HealthCheckResponse;
import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HealthCheckQueryService {

    private final HealthCheckRepository healthCheckRepository;
    private final ServiceMonitorService serviceMonitorService;

    public HealthCheckQueryService(
            HealthCheckRepository healthCheckRepository,
            ServiceMonitorService serviceMonitorService
    ) {
        this.healthCheckRepository = healthCheckRepository;
        this.serviceMonitorService = serviceMonitorService;
    }

    @Transactional(readOnly = true)
    public List<HealthCheckResponse> findRecent(Long serviceId, int limit) {
        serviceMonitorService.getEntity(serviceId);
        return healthCheckRepository.findByMonitoredServiceIdOrderByCheckedAtDesc(
                        serviceId,
                        PageRequest.of(0, Math.min(Math.max(limit, 1), 500))
                ).stream()
                .map(this::toResponse)
                .toList();
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
