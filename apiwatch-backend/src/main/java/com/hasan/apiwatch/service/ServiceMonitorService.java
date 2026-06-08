package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.CreateServiceRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServiceMonitorService {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;

    public ServiceMonitorService(
            MonitoredServiceRepository serviceRepository,
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository
    ) {
        this.serviceRepository = serviceRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        String name = request.name().trim();
        if (serviceRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A service with this name already exists");
        }

        MonitoredService service = new MonitoredService();
        apply(
                service,
                name,
                request.url().trim(),
                request.method() == null ? HttpMethodType.GET : request.method(),
                request.expectedStatusCode() == null ? 200 : request.expectedStatusCode(),
                request.timeoutMs() == null ? 2000 : request.timeoutMs(),
                request.failureThreshold() == null ? 3 : request.failureThreshold(),
                request.active() == null || request.active()
        );
        return toResponse(serviceRepository.save(service));
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> findAll() {
        return serviceRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
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

        apply(
                service,
                name,
                request.url().trim(),
                request.method(),
                request.expectedStatusCode(),
                request.timeoutMs(),
                request.failureThreshold(),
                request.active()
        );
        return toResponse(serviceRepository.save(service));
    }

    @Transactional
    public void delete(Long id) {
        serviceRepository.delete(getEntity(id));
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
            int expectedStatusCode,
            int timeoutMs,
            int failureThreshold,
            boolean active
    ) {
        service.setName(name);
        service.setUrl(url);
        service.setMethod(method);
        service.setExpectedStatusCode(expectedStatusCode);
        service.setTimeoutMs(timeoutMs);
        service.setFailureThreshold(failureThreshold);
        service.setActive(active);
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
                service.getTimeoutMs(),
                service.getFailureThreshold(),
                service.isActive(),
                latest == null ? HealthStatus.UNKNOWN : latest.getStatus(),
                latest == null ? null : latest.getCheckedAt(),
                latest == null ? null : latest.getResponseTimeMs(),
                activeIncident,
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }
}
