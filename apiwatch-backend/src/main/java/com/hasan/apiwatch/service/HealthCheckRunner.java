package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.HealthCheckResponse;
import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
public class HealthCheckRunner {

    private final WebClient webClient;
    private final HealthCheckRepository healthCheckRepository;
    private final ServiceMonitorService serviceMonitorService;
    private final IncidentService incidentService;

    public HealthCheckRunner(
            WebClient.Builder webClientBuilder,
            HealthCheckRepository healthCheckRepository,
            ServiceMonitorService serviceMonitorService,
            IncidentService incidentService
    ) {
        this.webClient = webClientBuilder.build();
        this.healthCheckRepository = healthCheckRepository;
        this.serviceMonitorService = serviceMonitorService;
        this.incidentService = incidentService;
    }

    @Transactional
    public HealthCheckResponse run(Long serviceId) {
        MonitoredService service = serviceMonitorService.getEntity(serviceId);
        return run(service);
    }

    @Transactional
    public HealthCheckResponse run(MonitoredService service) {
        long startedAt = System.nanoTime();
        Integer statusCode = null;
        String errorMessage = null;
        HealthStatus status;

        try {
            long hardTimeoutMs = Math.min(120_000L, service.getTimeoutMs() + 1_000L);
            statusCode = webClient.get()
                    .uri(service.getUrl())
                    .exchangeToMono(response ->
                            response.releaseBody().thenReturn(response.statusCode().value())
                    )
                    .timeout(Duration.ofMillis(hardTimeoutMs))
                    .block();
            long responseTimeMs = elapsedMilliseconds(startedAt);
            status = classify(
                    statusCode,
                    service.getExpectedStatusCode(),
                    responseTimeMs,
                    service.getTimeoutMs()
            );
        } catch (Exception exception) {
            status = HealthStatus.DOWN;
            errorMessage = readableMessage(exception);
        }

        HealthCheck check = new HealthCheck();
        check.setMonitoredService(service);
        check.setStatus(status);
        check.setHttpStatusCode(statusCode);
        check.setResponseTimeMs(elapsedMilliseconds(startedAt));
        check.setErrorMessage(errorMessage);
        HealthCheck saved = healthCheckRepository.save(check);
        incidentService.evaluate(service, saved);
        return toResponse(saved);
    }

    public HealthStatus classify(
            int actualStatusCode,
            int expectedStatusCode,
            long responseTimeMs,
            int slowThresholdMs
    ) {
        if (actualStatusCode != expectedStatusCode) {
            return HealthStatus.DOWN;
        }
        return responseTimeMs > slowThresholdMs ? HealthStatus.SLOW : HealthStatus.UP;
    }

    private long elapsedMilliseconds(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String readableMessage(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private HealthCheckResponse toResponse(HealthCheck check) {
        return new HealthCheckResponse(
                check.getId(),
                check.getMonitoredService().getId(),
                check.getStatus(),
                check.getHttpStatusCode(),
                check.getResponseTimeMs(),
                check.getErrorMessage(),
                check.getCheckedAt()
        );
    }
}
