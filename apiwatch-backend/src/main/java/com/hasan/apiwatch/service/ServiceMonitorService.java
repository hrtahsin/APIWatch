package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.CreateServiceRequest;
import com.hasan.apiwatch.dto.PageResponse;
import com.hasan.apiwatch.dto.ServiceResponse;
import com.hasan.apiwatch.dto.UpdateServiceRequest;
import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.AuditAction;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.HttpMethodType;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.exception.BadRequestException;
import com.hasan.apiwatch.exception.ResourceNotFoundException;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ServiceMonitorService {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final ServiceCredentialService credentialService;
    private final UrlSafetyService urlSafetyService;
    private final AuditLogService auditLogService;

    public ServiceMonitorService(
            MonitoredServiceRepository serviceRepository,
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository,
            NotificationDeliveryRepository notificationDeliveryRepository,
            ServiceCredentialService credentialService,
            UrlSafetyService urlSafetyService,
            AuditLogService auditLogService
    ) {
        this.serviceRepository = serviceRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.credentialService = credentialService;
        this.urlSafetyService = urlSafetyService;
        this.auditLogService = auditLogService;
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
                request.ownerName(),
                request.teamName(),
                request.tags(),
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
        MonitoredService saved = serviceRepository.save(service);
        auditLogService.record(
                AuditAction.SERVICE_CREATED,
                "SERVICE",
                saved.getId(),
                saved.getName(),
                "Registered " + saved.getMethod() + " " + saved.getUrl()
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> findAll(int page, int size) {
        return findAll(page, size, null, null, "name", "asc");
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> findAll(
            int page,
            int size,
            String query,
            Boolean active,
            String sort,
            String direction
    ) {
        var services = serviceRepository.findAll(
                serviceSpecification(query, active),
                PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 100),
                        resolveSort(sort, direction)
                )
        ).map(this::toResponse);
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
                request.ownerName(),
                request.teamName(),
                request.tags(),
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
        MonitoredService saved = serviceRepository.save(service);
        auditLogService.record(
                AuditAction.SERVICE_UPDATED,
                "SERVICE",
                saved.getId(),
                saved.getName(),
                "Updated service configuration"
        );
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        MonitoredService service = getEntity(id);
        notificationDeliveryRepository.deleteByServiceId(id);
        healthCheckRepository.deleteByMonitoredServiceId(id);
        incidentRepository.deleteByMonitoredServiceId(id);
        serviceRepository.delete(service);
        auditLogService.record(
                AuditAction.SERVICE_DELETED,
                "SERVICE",
                id,
                service.getName(),
                "Deleted service and related history"
        );
    }

    @Transactional
    public ServiceResponse setActive(Long id, boolean active) {
        MonitoredService service = getEntity(id);
        boolean changed = service.isActive() != active;
        service.setActive(active);
        MonitoredService saved = serviceRepository.save(service);
        if (changed) {
            auditLogService.record(
                    active ? AuditAction.SERVICE_RESUMED : AuditAction.SERVICE_PAUSED,
                    "SERVICE",
                    saved.getId(),
                    saved.getName(),
                    active ? "Resumed scheduled monitoring" : "Paused scheduled monitoring"
            );
        }
        return toResponse(saved);
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
            String ownerName,
            String teamName,
            List<String> tags,
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
        service.setOwnerName(normalizeOptional(ownerName));
        service.setTeamName(normalizeOptional(teamName));
        service.setTags(normalizeTags(tags));
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

    private Sort resolveSort(String sort, String direction) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        String property = switch ((sort == null ? "name" : sort.trim()).toLowerCase(Locale.ROOT)) {
            case "owner", "ownername" -> "ownerName";
            case "team", "teamname" -> "teamName";
            case "created", "createdat" -> "createdAt";
            case "updated", "updatedat" -> "updatedAt";
            default -> "name";
        };
        return Sort.by(sortDirection, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private Specification<MonitoredService> serviceSpecification(String query, Boolean active) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), active));
            }

            String normalizedQuery = normalizeOptional(query);
            if (normalizedQuery != null) {
                String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("url")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("ownerName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("teamName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("tags")), pattern)
                ));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        List<String> normalized = tags.stream()
                .map(this::normalizeOptional)
                .filter(tag -> tag != null)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return null;
        }
        String joinedTags = String.join(",", normalized);
        if (joinedTags.length() > 500) {
            throw new BadRequestException("Combined tags cannot exceed 500 characters");
        }
        return joinedTags;
    }

    private List<String> tagsToList(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return List.of(tags.split(","));
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
                service.getOwnerName(),
                service.getTeamName(),
                tagsToList(service.getTags()),
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
