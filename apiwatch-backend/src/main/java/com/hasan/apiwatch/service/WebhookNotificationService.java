package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.NotificationDeliveryResponse;
import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.event.IncidentNotificationEvent;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebhookNotificationService {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final NotificationSettingsService settingsService;
    private final NotificationDeliveryRepository deliveryRepository;
    private final UrlSafetyService urlSafetyService;

    public WebhookNotificationService(
            WebClient.Builder webClientBuilder,
            NotificationSettingsService settingsService,
            NotificationDeliveryRepository deliveryRepository,
            UrlSafetyService urlSafetyService
    ) {
        this.webClient = webClientBuilder.build();
        this.settingsService = settingsService;
        this.deliveryRepository = deliveryRepository;
        this.urlSafetyService = urlSafetyService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(IncidentNotificationEvent event) {
        settingsService.loadTarget().ifPresent(target -> deliver(event, target));
    }

    public List<NotificationDeliveryResponse> findRecentDeliveries() {
        return deliveryRepository.findTop50ByOrderByAttemptedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    void deliver(
            IncidentNotificationEvent event,
            NotificationSettingsService.NotificationTarget target
    ) {
        if (isWithinCooldown(event, target.cooldownSeconds())) {
            saveDelivery(event, NotificationDeliveryStatus.SKIPPED_COOLDOWN, null, null);
            return;
        }

        try {
            urlSafetyService.assertRequestAllowed(target.webhookUrl());
            Integer statusCode = webClient.post()
                    .uri(target.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload(event))
                    .exchangeToMono(response -> response.releaseBody()
                            .thenReturn(response.statusCode().value()))
                    .timeout(DELIVERY_TIMEOUT)
                    .block();
            if (statusCode != null && statusCode >= 200 && statusCode < 300) {
                saveDelivery(event, NotificationDeliveryStatus.SENT, statusCode, null);
            } else {
                saveDelivery(
                        event,
                        NotificationDeliveryStatus.FAILED,
                        statusCode,
                        "Webhook returned a non-success HTTP status"
                );
            }
        } catch (Exception exception) {
            saveDelivery(
                    event,
                    NotificationDeliveryStatus.FAILED,
                    null,
                    readableMessage(exception)
            );
        }
    }

    private boolean isWithinCooldown(IncidentNotificationEvent event, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return false;
        }
        Instant cutoff = Instant.now().minusSeconds(cooldownSeconds);
        return deliveryRepository
                .findFirstByServiceIdAndEventTypeAndStatusOrderByAttemptedAtDesc(
                        event.serviceId(),
                        event.eventType(),
                        NotificationDeliveryStatus.SENT
                )
                .map(delivery -> delivery.getAttemptedAt().isAfter(cutoff))
                .orElse(false);
    }

    private Map<String, Object> payload(IncidentNotificationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event.eventType().name());
        payload.put("incidentId", event.incidentId());
        payload.put("serviceId", event.serviceId());
        payload.put("serviceName", event.serviceName());
        payload.put("reason", event.reason());
        payload.put("startedAt", event.startedAt());
        payload.put("resolvedAt", event.resolvedAt());
        payload.put("durationSeconds", event.durationSeconds());
        payload.put("text", event.eventType() + ": " + event.serviceName() + " - " + event.reason());
        return payload;
    }

    private void saveDelivery(
            IncidentNotificationEvent event,
            NotificationDeliveryStatus status,
            Integer httpStatusCode,
            String errorMessage
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setIncidentId(event.incidentId());
        delivery.setServiceId(event.serviceId());
        delivery.setEventType(event.eventType());
        delivery.setStatus(status);
        delivery.setHttpStatusCode(httpStatusCode);
        delivery.setErrorMessage(errorMessage);
        deliveryRepository.save(delivery);
    }

    private String readableMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = current.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private NotificationDeliveryResponse toResponse(NotificationDelivery delivery) {
        return new NotificationDeliveryResponse(
                delivery.getId(),
                delivery.getIncidentId(),
                delivery.getServiceId(),
                delivery.getEventType(),
                delivery.getStatus(),
                delivery.getHttpStatusCode(),
                delivery.getErrorMessage(),
                delivery.getAttemptedAt()
        );
    }
}
