package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.DashboardSummaryResponse;
import com.hasan.apiwatch.dto.ServiceMetricsResponse;
import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class MetricsService {

    private final MonitoredServiceRepository serviceRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;
    private final ServiceMonitorService serviceMonitorService;

    public MetricsService(
            MonitoredServiceRepository serviceRepository,
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository,
            ServiceMonitorService serviceMonitorService
    ) {
        this.serviceRepository = serviceRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
        this.serviceMonitorService = serviceMonitorService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse dashboardSummary() {
        List<MonitoredService> services = serviceRepository.findAllByOrderByNameAsc();
        List<HealthCheck> latestChecks = services.stream()
                .map(service -> healthCheckRepository
                        .findTopByMonitoredServiceIdOrderByCheckedAtDesc(service.getId())
                        .orElse(null))
                .toList();

        long up = latestChecks.stream().filter(check -> hasStatus(check, HealthStatus.UP)).count();
        long slow = latestChecks.stream().filter(check -> hasStatus(check, HealthStatus.SLOW)).count();
        long down = latestChecks.stream().filter(check -> hasStatus(check, HealthStatus.DOWN)).count();
        long unknown = latestChecks.stream().filter(check -> check == null).count();
        double averageResponse = latestChecks.stream()
                .filter(check -> check != null && check.getResponseTimeMs() != null)
                .mapToLong(HealthCheck::getResponseTimeMs)
                .average()
                .orElse(0);

        long known = up + slow + down;
        double uptime = known == 0 ? 0 : percentage(up + slow, known);
        return new DashboardSummaryResponse(
                services.size(),
                up,
                slow,
                down,
                unknown,
                incidentRepository.countByStatus(IncidentStatus.ACTIVE),
                round(averageResponse),
                round(uptime)
        );
    }

    @Transactional(readOnly = true)
    public ServiceMetricsResponse serviceMetrics(Long serviceId, int windowHours) {
        int safeWindow = Math.min(Math.max(windowHours, 1), 24 * 90);
        MonitoredService service = serviceMonitorService.getEntity(serviceId);
        Instant since = Instant.now().minus(safeWindow, ChronoUnit.HOURS);
        List<HealthCheck> checks =
                healthCheckRepository.findByMonitoredServiceIdAndCheckedAtGreaterThanEqualOrderByCheckedAtAsc(
                        serviceId,
                        since
                );
        long failed = checks.stream().filter(check -> check.getStatus() == HealthStatus.DOWN).count();
        long slow = checks.stream().filter(check -> check.getStatus() == HealthStatus.SLOW).count();
        double uptime = checks.isEmpty() ? 0 : percentage(checks.size() - failed, checks.size());
        List<Long> responseTimes = checks.stream()
                .map(HealthCheck::getResponseTimeMs)
                .filter(value -> value != null)
                .sorted(Comparator.naturalOrder())
                .toList();
        double average = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double p95 = percentile95(responseTimes);
        HealthStatus latest = checks.isEmpty()
                ? HealthStatus.UNKNOWN
                : checks.get(checks.size() - 1).getStatus();

        return new ServiceMetricsResponse(
                service.getId(),
                service.getName(),
                safeWindow,
                round(uptime),
                round(average),
                round(p95),
                checks.size(),
                failed,
                slow,
                latest
        );
    }

    private boolean hasStatus(HealthCheck check, HealthStatus status) {
        return check != null && check.getStatus() == status;
    }

    private double percentile95(List<Long> sortedValues) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, (int) Math.ceil(sortedValues.size() * 0.95) - 1);
        return sortedValues.get(index);
    }

    private double percentage(long numerator, long denominator) {
        return numerator * 100.0 / denominator;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
