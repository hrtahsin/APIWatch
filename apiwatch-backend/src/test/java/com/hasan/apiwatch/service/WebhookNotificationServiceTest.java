package com.hasan.apiwatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;
import com.hasan.apiwatch.enums.NotificationProvider;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.event.IncidentNotificationEvent;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookNotificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecretEncryptionService encryptionService = new SecretEncryptionService(
            "YXBpd2F0Y2gtZGV2LWVuY3J5cHRpb24ta2V5LTMyYiE="
    );

    @Test
    void queuesIncidentNotificationForAsyncDelivery() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.findFirstByServiceIdAndEventTypeAndStatusOrderByAttemptedAtDesc(
                7L,
                NotificationEventType.INCIDENT_OPENED,
                NotificationDeliveryStatus.SENT
        )).thenReturn(Optional.empty());
        WebhookNotificationService service = service(repository);
        MonitoredService monitoredService = new MonitoredService();

        service.enqueue(event(), monitoredService, target());

        ArgumentCaptor<NotificationDelivery> captor =
                ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryStatus.PENDING);
        assertThat(captor.getValue().getProvider()).isEqualTo(NotificationProvider.WEBHOOK);
        assertThat(captor.getValue().getPayloadJson()).contains("INCIDENT_OPENED");
        assertThat(captor.getValue().getDestinationEncrypted()).isNotBlank();
    }

    @Test
    void recordsSuccessfulPendingWebhookDelivery() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        WebhookNotificationService service = service(
                repository,
                WebClient.builder().exchangeFunction(request -> Mono.just(
                        ClientResponse.create(HttpStatus.NO_CONTENT).build()
                ))
        );
        NotificationDelivery delivery = pendingDelivery();

        service.deliverPending(delivery);

        ArgumentCaptor<NotificationDelivery> captor =
                ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
        assertThat(captor.getValue().getHttpStatusCode()).isEqualTo(204);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
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
        WebhookNotificationService service = service(repository);

        service.enqueue(event(), new MonitoredService(), target());

        ArgumentCaptor<NotificationDelivery> captor =
                ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(NotificationDeliveryStatus.SKIPPED_COOLDOWN);
    }

    private WebhookNotificationService service(NotificationDeliveryRepository repository) {
        return service(
                repository,
                WebClient.builder().exchangeFunction(request -> Mono.error(
                        new AssertionError("Delivery should not be attempted")
                ))
        );
    }

    @SuppressWarnings("unchecked")
    private WebhookNotificationService service(
            NotificationDeliveryRepository repository,
            WebClient.Builder webClientBuilder
    ) {
        return new WebhookNotificationService(
                webClientBuilder,
                mock(NotificationSettingsService.class),
                repository,
                mock(MonitoredServiceRepository.class),
                activeIncidentRepository(),
                encryptionService,
                new UrlSafetyService(false, ""),
                objectMapper,
                mock(ObjectProvider.class),
                3,
                60,
                "apiwatch@example.com"
        );
    }

    private IncidentRepository activeIncidentRepository() {
        Incident incident = new Incident();
        incident.setStatus(IncidentStatus.ACTIVE);
        IncidentRepository repository = mock(IncidentRepository.class);
        when(repository.findById(11L)).thenReturn(Optional.of(incident));
        return repository;
    }

    private NotificationSettingsService.NotificationTarget target() {
        return new NotificationSettingsService.NotificationTarget(
                NotificationProvider.WEBHOOK,
                "https://hooks.example.com/incidents",
                "https://hooks.example.com/****",
                300,
                0
        );
    }

    private NotificationDelivery pendingDelivery() {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setIncidentId(11L);
        delivery.setServiceId(7L);
        delivery.setProvider(NotificationProvider.WEBHOOK);
        delivery.setDestinationDisplay("https://hooks.example.com/****");
        delivery.setDestinationEncrypted(encryptionService.encrypt("https://hooks.example.com/incidents"));
        delivery.setEventType(NotificationEventType.INCIDENT_OPENED);
        delivery.setStatus(NotificationDeliveryStatus.PENDING);
        delivery.setPayloadJson(payloadJson());
        delivery.setNextAttemptAt(Instant.now());
        return delivery;
    }

    private String payloadJson() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "event", "INCIDENT_OPENED",
                    "incidentId", 11L,
                    "serviceId", 7L,
                    "serviceName", "Payments",
                    "reason", "3 consecutive health checks failed",
                    "startedAt", Instant.now().minusSeconds(120).toString(),
                    "text", "INCIDENT_OPENED: Payments - 3 consecutive health checks failed"
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
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
