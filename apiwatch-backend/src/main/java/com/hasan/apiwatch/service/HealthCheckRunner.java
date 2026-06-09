package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.HealthCheckResponse;
import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.FailureType;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.exception.CheckAlreadyRunningException;
import com.hasan.apiwatch.exception.ServiceRateLimitedException;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

@Service
public class HealthCheckRunner {

    private static final Duration DEFAULT_RATE_LIMIT_PAUSE = Duration.ofMinutes(1);

    private final WebClient webClient;
    private final HealthCheckRepository healthCheckRepository;
    private final ServiceMonitorService serviceMonitorService;
    private final IncidentService incidentService;
    private final ServiceCredentialService credentialService;
    private final Set<Long> runningServiceIds = ConcurrentHashMap.newKeySet();

    public HealthCheckRunner(
            WebClient.Builder webClientBuilder,
            HealthCheckRepository healthCheckRepository,
            ServiceMonitorService serviceMonitorService,
            IncidentService incidentService,
            ServiceCredentialService credentialService
    ) {
        this.webClient = webClientBuilder.build();
        this.healthCheckRepository = healthCheckRepository;
        this.serviceMonitorService = serviceMonitorService;
        this.incidentService = incidentService;
        this.credentialService = credentialService;
    }

    @Transactional
    public HealthCheckResponse run(Long serviceId) {
        return run(serviceMonitorService.getEntity(serviceId));
    }

    @Transactional
    public HealthCheckResponse run(MonitoredService service) {
        Long serviceId = service.getId();
        Instant rateLimitedUntil = service.getRateLimitedUntil();
        if (rateLimitedUntil != null && rateLimitedUntil.isAfter(Instant.now())) {
            throw new ServiceRateLimitedException(serviceId, rateLimitedUntil);
        }
        if (!runningServiceIds.add(serviceId)) {
            throw new CheckAlreadyRunningException(serviceId);
        }

        try {
            return execute(service);
        } finally {
            runningServiceIds.remove(serviceId);
        }
    }

    private HealthCheckResponse execute(MonitoredService service) {
        long startedAt = System.nanoTime();
        Integer statusCode = null;
        String errorMessage = null;
        FailureType failureType = null;
        Long retryAfterSeconds = null;
        Long rateLimitRemaining = null;
        Instant rateLimitResetAt = null;
        HealthStatus status;

        try {
            long hardTimeoutMs = Math.min(120_000L, service.getTimeoutMs() + 1_000L);
            EndpointResponse endpointResponse = webClient.get()
                    .uri(service.getUrl())
                    .headers(headers -> credentialService.applyTo(service, headers))
                    .exchangeToMono(response -> {
                        HttpHeaders headers = new HttpHeaders();
                        headers.putAll(response.headers().asHttpHeaders());
                        if (service.getResponseBodyContains() == null) {
                            return response.releaseBody().thenReturn(
                                    new EndpointResponse(
                                            response.statusCode().value(),
                                            headers,
                                            null
                                    )
                            );
                        }
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new EndpointResponse(
                                        response.statusCode().value(),
                                        headers,
                                        body
                                ));
                    })
                    .timeout(Duration.ofMillis(hardTimeoutMs))
                    .block();

            if (endpointResponse == null) {
                throw new IllegalStateException("Endpoint returned no response");
            }

            statusCode = endpointResponse.statusCode();
            rateLimitRemaining = parseLongHeader(endpointResponse.headers(), "X-RateLimit-Remaining");
            rateLimitResetAt = parseEpochHeader(endpointResponse.headers(), "X-RateLimit-Reset");
            boolean rateLimited = isRateLimited(statusCode, rateLimitRemaining);
            long responseTimeMs = elapsedMilliseconds(startedAt);

            if (rateLimited) {
                retryAfterSeconds = resolveRetryAfterSeconds(endpointResponse.headers(), rateLimitResetAt);
                Instant retryAt = Instant.now().plusSeconds(retryAfterSeconds);
                if (rateLimitResetAt != null && rateLimitResetAt.isAfter(retryAt)) {
                    retryAt = rateLimitResetAt;
                }
                serviceMonitorService.setRateLimitedUntil(service.getId(), retryAt);
                status = HealthStatus.RATE_LIMITED;
                failureType = FailureType.RATE_LIMITED;
                errorMessage = "Endpoint rate limit reached; checks paused until " + retryAt;
            } else {
                serviceMonitorService.clearRateLimit(service.getId());
                status = classify(
                        statusCode,
                        service.getExpectedStatusMin(),
                        service.getExpectedStatusMax(),
                        responseTimeMs,
                        service.getTimeoutMs()
                );
                if (status == HealthStatus.DOWN) {
                    failureType = FailureType.HTTP_STATUS;
                    errorMessage = "Expected HTTP " + formatExpectedStatus(service)
                            + " but received " + statusCode;
                } else if (service.getResponseBodyContains() != null
                        && !endpointResponse.body().contains(service.getResponseBodyContains())) {
                    status = HealthStatus.DOWN;
                    failureType = FailureType.RESPONSE_VALIDATION;
                    errorMessage = "Response body did not contain the configured text";
                }
            }
        } catch (Exception exception) {
            status = HealthStatus.DOWN;
            failureType = classifyFailure(exception);
            errorMessage = readableMessage(exception);
        }

        HealthCheck check = new HealthCheck();
        check.setMonitoredService(service);
        check.setStatus(status);
        check.setHttpStatusCode(statusCode);
        check.setResponseTimeMs(elapsedMilliseconds(startedAt));
        check.setFailureType(failureType);
        check.setErrorMessage(errorMessage);
        check.setRetryAfterSeconds(retryAfterSeconds);
        check.setRateLimitRemaining(rateLimitRemaining);
        check.setRateLimitResetAt(rateLimitResetAt);
        HealthCheck saved = healthCheckRepository.save(check);
        incidentService.evaluate(service, saved);
        return toResponse(saved);
    }

    public HealthStatus classify(
            int actualStatusCode,
            int expectedStatusMin,
            int expectedStatusMax,
            long responseTimeMs,
            int slowThresholdMs
    ) {
        if (actualStatusCode < expectedStatusMin || actualStatusCode > expectedStatusMax) {
            return HealthStatus.DOWN;
        }
        return responseTimeMs > slowThresholdMs ? HealthStatus.SLOW : HealthStatus.UP;
    }

    private String formatExpectedStatus(MonitoredService service) {
        if (service.getExpectedStatusMin() == service.getExpectedStatusMax()) {
            return Integer.toString(service.getExpectedStatusMin());
        }
        return service.getExpectedStatusMin() + "-" + service.getExpectedStatusMax();
    }

    boolean isRateLimited(int statusCode, Long remaining) {
        return statusCode == 429 || (statusCode == 403 && remaining != null && remaining == 0);
    }

    FailureType classifyFailure(Exception exception) {
        Throwable cause = rootCause(exception);
        if (cause instanceof TimeoutException) {
            return FailureType.TIMEOUT;
        }
        if (cause instanceof UnknownHostException) {
            return FailureType.DNS_FAILURE;
        }
        if (cause instanceof ConnectException) {
            return FailureType.CONNECTION_FAILURE;
        }
        if (exception instanceof WebClientRequestException) {
            return FailureType.NETWORK_ERROR;
        }
        return FailureType.NETWORK_ERROR;
    }

    private long resolveRetryAfterSeconds(HttpHeaders headers, Instant rateLimitResetAt) {
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            try {
                return Math.max(1, Long.parseLong(retryAfter.trim()));
            } catch (NumberFormatException ignored) {
                try {
                    Instant retryAt = ZonedDateTime.parse(
                            retryAfter,
                            DateTimeFormatter.RFC_1123_DATE_TIME
                    ).toInstant();
                    return Math.max(1, Duration.between(Instant.now(), retryAt).toSeconds());
                } catch (DateTimeParseException ignoredDate) {
                    // Fall through to the reset header or default pause.
                }
            }
        }
        if (rateLimitResetAt != null) {
            return Math.max(1, Duration.between(Instant.now(), rateLimitResetAt).toSeconds());
        }
        return DEFAULT_RATE_LIMIT_PAUSE.toSeconds();
    }

    private Long parseLongHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Instant parseEpochHeader(HttpHeaders headers, String name) {
        Long epochSeconds = parseLongHeader(headers, name);
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private long elapsedMilliseconds(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String readableMessage(Exception exception) {
        Throwable cause = rootCause(exception);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
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

    private record EndpointResponse(int statusCode, HttpHeaders headers, String body) {
    }
}
