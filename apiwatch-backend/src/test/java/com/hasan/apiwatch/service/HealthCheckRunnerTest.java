package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.enums.FailureType;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.exception.CheckAlreadyRunningException;
import com.hasan.apiwatch.exception.ServiceRateLimitedException;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthCheckRunnerTest {

    private final HealthCheckRunner runner = new HealthCheckRunner(
            WebClient.builder(),
            mock(HealthCheckRepository.class),
            mock(ServiceMonitorService.class),
            mock(IncidentService.class)
    );

    @Test
    void classifiesMatchingFastResponseAsUp() {
        assertThat(runner.classify(200, 200, 180, 2000)).isEqualTo(HealthStatus.UP);
    }

    @Test
    void classifiesMatchingSlowResponseAsSlow() {
        assertThat(runner.classify(200, 200, 2501, 2000)).isEqualTo(HealthStatus.SLOW);
    }

    @Test
    void classifiesUnexpectedStatusAsDown() {
        assertThat(runner.classify(503, 200, 90, 2000)).isEqualTo(HealthStatus.DOWN);
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
        HealthCheckRepository repository = mock(HealthCheckRepository.class);
        when(repository.save(any(HealthCheck.class))).thenAnswer(invocation -> invocation.getArgument(0));
        HealthCheckRunner blockingRunner = new HealthCheckRunner(
                WebClient.builder().exchangeFunction(request -> Mono.never()),
                repository,
                mock(ServiceMonitorService.class),
                mock(IncidentService.class)
        );
        MonitoredService service = service(12L);
        service.setTimeoutMs(100);

        CompletableFuture<?> firstCheck = CompletableFuture.runAsync(() -> blockingRunner.run(service));
        TimeUnit.MILLISECONDS.sleep(100);

        assertThatThrownBy(() -> blockingRunner.run(service))
                .isInstanceOf(CheckAlreadyRunningException.class)
                .hasMessageContaining("already running");

        firstCheck.get(2, TimeUnit.SECONDS);
    }

    private MonitoredService service(Long id) {
        MonitoredService service = new MonitoredService();
        ReflectionTestUtils.setField(service, "id", id);
        service.setName("Test API");
        service.setUrl("https://example.com");
        service.setExpectedStatusCode(200);
        service.setTimeoutMs(2000);
        service.setFailureThreshold(3);
        service.setActive(true);
        return service;
    }
}
