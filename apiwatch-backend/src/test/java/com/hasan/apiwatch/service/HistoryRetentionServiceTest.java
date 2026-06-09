package com.hasan.apiwatch.service;

import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistoryRetentionServiceTest {

    @Test
    void deletesOnlyHistoryBeyondConfiguredRetentionWindows() {
        HealthCheckRepository checks = mock(HealthCheckRepository.class);
        IncidentRepository incidents = mock(IncidentRepository.class);
        NotificationDeliveryRepository deliveries =
                mock(NotificationDeliveryRepository.class);
        Instant now = Instant.parse("2026-06-09T03:00:00Z");
        when(checks.deleteByCheckedAtBefore(now.minusSeconds(90L * 86_400))).thenReturn(12L);
        when(incidents.deleteByStatusAndResolvedAtBefore(
                IncidentStatus.RESOLVED,
                now.minusSeconds(365L * 86_400)
        )).thenReturn(3L);
        when(deliveries.deleteByAttemptedAtBefore(now.minusSeconds(30L * 86_400)))
                .thenReturn(8L);
        HistoryRetentionService service = new HistoryRetentionService(
                checks,
                incidents,
                deliveries,
                90,
                365,
                30
        );

        var result = service.cleanup(now);

        assertThat(result.healthChecks()).isEqualTo(12);
        assertThat(result.incidents()).isEqualTo(3);
        assertThat(result.notificationDeliveries()).isEqualTo(8);
        verify(incidents).deleteByStatusAndResolvedAtBefore(
                IncidentStatus.RESOLVED,
                now.minusSeconds(365L * 86_400)
        );
    }
}
