package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;
import com.hasan.apiwatch.event.IncidentNotificationEvent;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookNotificationServiceTest {

    @Test
    void recordsSuccessfulWebhookDelivery() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.findFirstByServiceIdAndEventTypeAndStatusOrderByAttemptedAtDesc(
                7L,
                NotificationEventType.INCIDENT_OPENED,
                NotificationDeliveryStatus.SENT
        )).thenReturn(Optional.empty());
        WebhookNotificationService service = new WebhookNotificationService(
                WebClient.builder().exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.NO_CONTENT).build()
                )),
                mock(NotificationSettingsService.class),
                repository,
                new UrlSafetyService(false, "")
        );

        service.deliver(event(), new NotificationSettingsService.NotificationTarget(
                "https://hooks.example.com/incidents",
                300
        ));

        ArgumentCaptor<NotificationDelivery> captor =
                ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(captor.getValue().getHttpStatusCode()).isEqualTo(204);
    }

    @Test
    void skipsRepeatedEventInsideCooldown() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        NotificationDelivery previous = new NotificationDelivery();
        previous.setServiceId(7L);
        previous.setEventType(NotificationEventType.INCIDENT_OPENED);
        previous.setStatus(NotificationDeliveryStatus.SENT);
        ReflectionTestUtils.setField(previous, "attemptedAt", Instant.now().minusSeconds(30));
        when(repository.findFirstByServiceIdAndEventTypeAndStatusOrderByAttemptedAtDesc(
                7L,
                NotificationEventType.INCIDENT_OPENED,
                NotificationDeliveryStatus.SENT
        )).thenReturn(Optional.of(previous));
        WebhookNotificationService service = new WebhookNotificationService(
                WebClient.builder().exchangeFunction(request -> Mono.error(
                        new AssertionError("Webhook must not be called during cooldown")
                )),
                mock(NotificationSettingsService.class),
                repository,
                new UrlSafetyService(false, "")
        );

        service.deliver(event(), new NotificationSettingsService.NotificationTarget(
                "https://hooks.example.com/incidents",
                300
        ));

        ArgumentCaptor<NotificationDelivery> captor =
                ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(NotificationDeliveryStatus.SKIPPED_COOLDOWN);
    }

    private IncidentNotificationEvent event() {
        return new IncidentNotificationEvent(
                NotificationEventType.INCIDENT_OPENED,
                11L,
                7L,
                "Payments",
                "3 consecutive health checks failed",
                Instant.now().minusSeconds(120),
                null,
                null
        );
    }
}
