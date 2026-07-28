package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.HealthCheckResponse;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.FailureType;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.exception.CheckAlreadyRunningException;
import com.hasan.apiwatch.exception.ServiceRateLimitedException;
import com.hasan.apiwatch.exception.UnsafeTargetException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthCheckRunnerTest {

    private final HealthCheckRunner runner = new HealthCheckRunner(
            WebClient.builder(),
            mock(ServiceMonitorService.class),
            persistenceService(),
            mock(ServiceCredentialService.class),
            mock(UrlSafetyService.class)
    );

    @Test
    void classifiesMatchingFastResponseAsUp() {
        assertThat(runner.classify(204, 200, 299, 180, 2000)).isEqualTo(HealthStatus.UP);
    }

    @Test
    void classifiesMatchingSlowResponseAsSlow() {
        assertThat(runner.classify(200, 200, 299, 2501, 2000)).isEqualTo(HealthStatus.SLOW);
    }

    @Test
    void classifiesUnexpectedStatusAsDown() {
        assertThat(runner.classify(503, 200, 299, 90, 2000)).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void identifiesStandardAndGithubRateLimitResponses() {
        assertThat(runner.isRateLimited(429, null)).isTrue();
        assertThat(runner.isRateLimited(403, 0L)).isTrue();
        assertThat(runner.isRateLimited(403, 12L)).isFalse();
    }

    @Test
    void classifiesNetworkFailureTypes() {
        assertThat(runner.classifyFailure(new RuntimeException(new TimeoutException())))
                .isEqualTo(FailureType.TIMEOUT);
        assertThat(runner.classifyFailure(new RuntimeException(new UnknownHostException())))
                .isEqualTo(FailureType.DNS_FAILURE);
        assertThat(runner.classifyFailure(new RuntimeException(new ConnectException())))
                .isEqualTo(FailureType.CONNECTION_FAILURE);
        assertThat(runner.classifyFailure(new UnsafeTargetException("blocked")))
                .isEqualTo(FailureType.SECURITY_BLOCKED);
    }

    @Test
    void rejectsChecksWhileRateLimitPauseIsActive() {
        MonitoredService service = service(9L);
        service.setRateLimitedUntil(Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> runner.run(service))
                .isInstanceOf(ServiceRateLimitedException.class)
                .hasMessageContaining("rate limited");
    }

    @Test
    void rejectsConcurrentChecksForTheSameService() throws Exception {
        CountDownLatch requestStarted = new CountDownLatch(1);
        HealthCheckRunner blockingRunner = new HealthCheckRunner(
                WebClient.builder().exchangeFunction(request -> {
                    requestStarted.countDown();
                    return Mono.never();
                }),
                mock(ServiceMonitorService.class),
                persistenceService(),
                mock(ServiceCredentialService.class),
                mock(UrlSafetyService.class)
        );
        MonitoredService service = service(12L);
        service.setTimeoutMs(1000);

        CompletableFuture<?> firstCheck = CompletableFuture.runAsync(() -> blockingRunner.run(service));
        assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> blockingRunner.run(service))
                .isInstanceOf(CheckAlreadyRunningException.class)
                .hasMessageContaining("already running");

        firstCheck.get(2, TimeUnit.SECONDS);
    }

    @Test
    void marksCheckDownWhenResponseBodyValidationFails() {
        HealthCheckRunner validatingRunner = new HealthCheckRunner(
                WebClient.builder().exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .body("{\"status\":\"degraded\"}")
                                .build()
                )),
                mock(ServiceMonitorService.class),
                persistenceService(),
                mock(ServiceCredentialService.class),
                mock(UrlSafetyService.class)
        );
        MonitoredService service = service(15L);
        service.setResponseBodyContains("\"status\":\"ok\"");

        var response = validatingRunner.run(service);

        assertThat(response.status()).isEqualTo(HealthStatus.DOWN);
        assertThat(response.failureType()).isEqualTo(FailureType.RESPONSE_VALIDATION);
        assertThat(response.errorMessage()).contains("did not contain");
    }

    private MonitoredService service(Long id) {
        MonitoredService service = new MonitoredService();
        ReflectionTestUtils.setField(service, "id", id);
        service.setName("Test API");
        service.setUrl("https://example.com");
        service.setExpectedStatusCode(200);
        service.setExpectedStatusMin(200);
        service.setExpectedStatusMax(299);
        service.setTimeoutMs(2000);
        service.setSlowThresholdMs(2000);
        service.setCheckIntervalSeconds(60);
        service.setFailureThreshold(3);
        service.setActive(true);
        return service;
    }

    private static HealthCheckPersistenceService persistenceService() {
        HealthCheckPersistenceService persistenceService =
                mock(HealthCheckPersistenceService.class);
        when(persistenceService.persist(anyLong(), any(HealthCheckResult.class)))
                .thenAnswer(invocation -> {
                    Long serviceId = invocation.getArgument(0);
                    HealthCheckResult result = invocation.getArgument(1);
                    return new HealthCheckResponse(
                            1L,
                            serviceId,
                            result.status(),
                            result.httpStatusCode(),
                            result.responseTimeMs(),
                            result.failureType(),
                            result.errorMessage(),
                            result.retryAfterSeconds(),
                            result.rateLimitRemaining(),
                            result.rateLimitResetAt(),
                            Instant.now()
                    );
                });
        return persistenceService;
    }
}
