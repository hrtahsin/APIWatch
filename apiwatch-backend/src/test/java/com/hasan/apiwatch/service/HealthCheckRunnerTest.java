package com.hasan.apiwatch.service;

import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
}
