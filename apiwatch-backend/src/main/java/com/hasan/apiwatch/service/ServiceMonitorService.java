package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.CreateServiceRequest;
import com.hasan.apiwatch.dto.PageResponse;
import com.hasan.apiwatch.dto.ServiceResponse;
import com.hasan.apiwatch.dto.UpdateServiceRequest;
import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.HttpMethodType;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.exception.BadRequestException;
import com.hasan.apiwatch.exception.ResourceNotFoundException;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;

@Service
public class ServiceMonitorService {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final ServiceCredentialService credentialService;
    private final UrlSafetyService urlSafetyService;

    public ServiceMonitorService(
            MonitoredServiceRepository serviceRepository,
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository,
            NotificationDeliveryRepository notificationDeliveryRepository,
            ServiceCredentialService credentialService,
            UrlSafetyService urlSafetyService
    ) {
        this.serviceRepository = serviceRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.credentialService = credentialService;
        this.urlSafetyService = urlSafetyService;
    }

    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        String name = request.name().trim();
        if (serviceRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A service with this name already exists");
        }

        MonitoredService service = new MonitoredService();
        ExpectedStatusRange expectedStatus = resolveExpectedStatusRange(
                request.expectedStatusCode(),
                request.expectedStatusMin(),
                request.expectedStatusMax(),
                new ExpectedStatusRange(200, 299)
        );
        apply(
                service,
                name,
                urlSafetyService.validateConfiguration(request.url()),
                request.method() == null ? HttpMethodType.GET : request.method(),
                expectedStatus,
                request.timeoutMs() == null ? 2000 : request.timeoutMs(),
                request.checkIntervalSeconds() == null ? 60 : request.checkIntervalSeconds(),
                request.responseBodyContains(),
                request.failureThreshold() == null ? 3 : request.failureThreshold(),
                request.active() == null || request.active()
        );
        credentialService.applyCreate(
                service,
                request.customHeaders(),
                request.authType(),
                request.authHeaderName(),
                request.authValue()
        );
        return toResponse(serviceRepository.save(service));
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> findAll(int page, int size) {
        var services = serviceRepository.findAll(PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "name")
        )).map(this::toResponse);
        return PageResponse.from(services);
    }

    @Transactional(readOnly = true)
    public ServiceResponse findById(Long id) {
        return toResponse(getEntity(id));
    }

    @Transactional
    public ServiceResponse update(Long id, UpdateServiceRequest request) {
        MonitoredService service = getEntity(id);
        String name = request.name().trim();
        if (serviceRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BadRequestException("A service with this name already exists");
        }

        ExpectedStatusRange expectedStatus = resolveExpectedStatusRange(
                request.expectedStatusCode(),
                request.expectedStatusMin(),
                request.expectedStatusMax(),
                new ExpectedStatusRange(
                        service.getExpectedStatusMin(),
                        service.getExpectedStatusMax()
                )
        );
        apply(
                service,
                name,
                urlSafetyService.validateConfiguration(request.url()),
                request.method(),
                expectedStatus,
                request.timeoutMs(),
                request.checkIntervalSeconds(),
                request.responseBodyContains(),
                request.failureThreshold(),
                request.active()
        );
        credentialService.applyUpdate(
                service,
                request.customHeaders(),
                request.authType(),
                request.authHeaderName(),
                request.authValue(),
                Boolean.TRUE.equals(request.clearAuthSecret())
        );
        return toResponse(serviceRepository.save(service));
    }

    @Transactional
    public void delete(Long id) {
        MonitoredService service = getEntity(id);
        notificationDeliveryRepository.deleteByServiceId(id);
        healthCheckRepository.deleteByMonitoredServiceId(id);
        incidentRepository.deleteByMonitoredServiceId(id);
        serviceRepository.delete(service);
    }

    @Transactional
    public ServiceResponse setActive(Long id, boolean active) {
        MonitoredService service = getEntity(id);
        service.setActive(active);
        return toResponse(serviceRepository.save(service));
    }

    @Transactional
    public void setRateLimitedUntil(Long id, Instant rateLimitedUntil) {
        getEntity(id).setRateLimitedUntil(rateLimitedUntil);
    }

    @Transactional
    public void clearRateLimit(Long id) {
        getEntity(id).setRateLimitedUntil(null);
    }

    @Transactional(readOnly = true)
    public MonitoredService getEntity(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service " + id + " was not found"));
    }

    private void apply(
            MonitoredService service,
            String name,
            String url,
            HttpMethodType method,
            ExpectedStatusRange expectedStatus,
            int timeoutMs,
            int checkIntervalSeconds,
            String responseBodyContains,
            int failureThreshold,
            boolean active
    ) {
        service.setName(name);
        service.setUrl(url);
        service.setMethod(method);
        service.setExpectedStatusCode(expectedStatus.min());
        service.setExpectedStatusMin(expectedStatus.min());
        service.setExpectedStatusMax(expectedStatus.max());
        service.setTimeoutMs(timeoutMs);
        service.setCheckIntervalSeconds(checkIntervalSeconds);
        service.setResponseBodyContains(normalizeOptional(responseBodyContains));
        service.setFailureThreshold(failureThreshold);
        service.setActive(active);
    }

    private ExpectedStatusRange resolveExpectedStatusRange(
            Integer legacyStatusCode,
            Integer requestedMin,
            Integer requestedMax,
            ExpectedStatusRange fallback
    ) {
        if (requestedMin == null && requestedMax == null) {
            if (legacyStatusCode != null) {
                return new ExpectedStatusRange(legacyStatusCode, legacyStatusCode);
            }
            return fallback;
        }
        if (requestedMin == null || requestedMax == null) {
            throw new BadRequestException(
                    "Expected status minimum and maximum must be provided together"
            );
        }
        if (requestedMin > requestedMax) {
            throw new BadRequestException(
                    "Expected status minimum cannot be greater than the maximum"
            );
        }
        return new ExpectedStatusRange(requestedMin, requestedMax);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ServiceResponse toResponse(MonitoredService service) {
        HealthCheck latest = healthCheckRepository
                .findTopByMonitoredServiceIdOrderByCheckedAtDesc(service.getId())
                .orElse(null);
        boolean activeIncident = incidentRepository
                .findFirstByMonitoredServiceIdAndStatus(service.getId(), IncidentStatus.ACTIVE)
                .isPresent();

        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getUrl(),
                service.getMethod(),
                service.getExpectedStatusCode(),
                service.getExpectedStatusMin(),
                service.getExpectedStatusMax(),
                service.getTimeoutMs(),
                service.getCheckIntervalSeconds(),
                service.getResponseBodyContains(),
                service.getFailureThreshold(),
                service.isActive(),
                latest == null ? HealthStatus.UNKNOWN : latest.getStatus(),
                latest == null ? null : latest.getCheckedAt(),
                latest == null ? null : latest.getResponseTimeMs(),
                latest == null ? null : latest.getHttpStatusCode(),
                latest == null ? null : latest.getFailureType(),
                latest == null ? null : latest.getErrorMessage(),
                service.getRateLimitedUntil(),
                credentialService.customHeaderNames(service),
                service.getAuthType(),
                service.getAuthHeaderName(),
                credentialService.hasAuthSecret(service),
                activeIncident,
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }

    private record ExpectedStatusRange(int min, int max) {
    }
}
