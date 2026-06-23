package com.hasan.apiwatch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hasan.apiwatch.dto.NotificationDeliveryResponse;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;
import com.hasan.apiwatch.enums.NotificationProvider;
import com.hasan.apiwatch.event.IncidentNotificationEvent;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.MonitoredServiceRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class WebhookNotificationService {

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);
    private static final String PAGERDUTY_EVENTS_URL = "https://events.pagerduty.com/v2/enqueue";
    private static final String OPSGENIE_ALERTS_URL = "https://api.opsgenie.com/v2/alerts";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final WebClient webClient;
    private final NotificationSettingsService settingsService;
    private final NotificationDeliveryRepository deliveryRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final SecretEncryptionService encryptionService;
    private final UrlSafetyService urlSafetyService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final int maxAttempts;
    private final int retryDelaySeconds;
    private final String emailFrom;

    public WebhookNotificationService(
            WebClient.Builder webClientBuilder,
            NotificationSettingsService settingsService,
            NotificationDeliveryRepository deliveryRepository,
            MonitoredServiceRepository serviceRepository,
            IncidentRepository incidentRepository,
            SecretEncryptionService encryptionService,
            UrlSafetyService urlSafetyService,
            ObjectMapper objectMapper,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${apiwatch.notifications.max-attempts:3}") int maxAttempts,
            @Value("${apiwatch.notifications.retry-delay-seconds:60}") int retryDelaySeconds,
            @Value("${apiwatch.notifications.email-from:apiwatch@localhost}") String emailFrom
    ) {
        this.webClient = webClientBuilder.build();
        this.settingsService = settingsService;
        this.deliveryRepository = deliveryRepository;
        this.serviceRepository = serviceRepository;
        this.incidentRepository = incidentRepository;
        this.encryptionService = encryptionService;
        this.urlSafetyService = urlSafetyService;
        this.objectMapper = objectMapper;
        this.mailSenderProvider = mailSenderProvider;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelaySeconds = Math.max(1, retryDelaySeconds);
        this.emailFrom = emailFrom;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(IncidentNotificationEvent event) {
        Optional<NotificationSettingsService.NotificationTarget> target =
                settingsService.loadTarget();
        if (target.isEmpty()) {
            return;
        }
        serviceRepository.findById(event.serviceId())
                .filter(service -> shouldNotify(service, event.eventType()))
                .ifPresent(service -> enqueue(event, service, target.get()));
    }

    public List<NotificationDeliveryResponse> findRecentDeliveries() {
        return deliveryRepository.findTop50ByOrderByAttemptedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Scheduled(
            initialDelayString = "${apiwatch.notifications.initial-delay-ms:5000}",
            fixedDelayString = "${apiwatch.notifications.delivery-interval-ms:5000}"
    )
    public void processDueDeliveries() {
        List<NotificationDelivery> deliveries =
                deliveryRepository
                        .findTop25ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                                NotificationDeliveryStatus.PENDING,
                                Instant.now()
                        );
        deliveries.forEach(this::deliverPending);
    }

    void enqueue(
            IncidentNotificationEvent event,
            MonitoredService service,
            NotificationSettingsService.NotificationTarget target
    ) {
        if (isWithinCooldown(event, target.cooldownSeconds())) {
            saveDelivery(
                    event,
                    target,
                    NotificationDeliveryStatus.SKIPPED_COOLDOWN,
                    null,
                    null,
                    null,
                    null
            );
            return;
        }

        saveDelivery(
                event,
                target,
                NotificationDeliveryStatus.PENDING,
                null,
                null,
                payloadJson(event),
                nextAttemptAt(event, service, target)
        );
    }

    void deliverPending(NotificationDelivery delivery) {
        if (delivery.getStatus() != NotificationDeliveryStatus.PENDING) {
            return;
        }

        if (resolvedBeforeEscalation(delivery)) {
            delivery.setStatus(NotificationDeliveryStatus.SKIPPED_RESOLVED);
            delivery.setNextAttemptAt(null);
            delivery.setErrorMessage("Incident resolved before the escalation delay elapsed");
            deliveryRepository.save(delivery);
            return;
        }

        int attemptCount = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(attemptCount);

        DispatchResult result;
        try {
            result = dispatch(delivery);
        } catch (Exception exception) {
            result = DispatchResult.failed(null, readableMessage(exception));
        }

        delivery.setHttpStatusCode(result.httpStatusCode());
        delivery.setErrorMessage(result.errorMessage());
        if (result.success()) {
            delivery.setStatus(NotificationDeliveryStatus.SENT);
            delivery.setNextAttemptAt(null);
        } else if (attemptCount < maxAttempts) {
            delivery.setStatus(NotificationDeliveryStatus.PENDING);
            delivery.setNextAttemptAt(Instant.now().plusSeconds((long) retryDelaySeconds * attemptCount));
        } else {
            delivery.setStatus(NotificationDeliveryStatus.FAILED);
            delivery.setNextAttemptAt(null);
        }
        deliveryRepository.save(delivery);
    }

    private boolean shouldNotify(MonitoredService service, NotificationEventType eventType) {
        return switch (eventType) {
            case INCIDENT_OPENED -> service.isNotifyOnIncidentOpen();
            case INCIDENT_RESOLVED -> service.isNotifyOnIncidentResolve();
        };
    }

    private Instant nextAttemptAt(
            IncidentNotificationEvent event,
            MonitoredService service,
            NotificationSettingsService.NotificationTarget target
    ) {
        if (event.eventType() != NotificationEventType.INCIDENT_OPENED) {
            return Instant.now();
        }

        int escalationMinutes = Math.max(
                service.getNotificationEscalationMinutes(),
                target.escalationMinutes()
        );
        if (escalationMinutes <= 0 || event.startedAt() == null) {
            return Instant.now();
        }
        Instant escalatedAt = event.startedAt().plus(Duration.ofMinutes(escalationMinutes));
        return escalatedAt.isAfter(Instant.now()) ? escalatedAt : Instant.now();
    }

    private boolean resolvedBeforeEscalation(NotificationDelivery delivery) {
        if (delivery.getEventType() != NotificationEventType.INCIDENT_OPENED
                || delivery.getAttemptCount() > 0) {
            return false;
        }
        return incidentRepository.findById(delivery.getIncidentId())
                .map(incident -> incident.getStatus() == IncidentStatus.RESOLVED)
                .orElse(true);
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

    private DispatchResult dispatch(NotificationDelivery delivery) {
        String destination = encryptionService.decrypt(delivery.getDestinationEncrypted());
        Map<String, Object> payload = readPayload(delivery.getPayloadJson());
        return switch (delivery.getProvider()) {
            case WEBHOOK -> postJson(destination, payload, headers -> {
            });
            case SLACK -> postJson(destination, slackPayload(payload), headers -> {
            });
            case DISCORD -> postJson(destination, discordPayload(payload), headers -> {
            });
            case EMAIL -> sendEmail(destination, payload);
            case PAGERDUTY -> postJson(PAGERDUTY_EVENTS_URL, pagerDutyPayload(destination, payload), headers -> {
            });
            case OPSGENIE -> sendOpsgenie(destination, payload);
        };
    }

    private DispatchResult postJson(
            String url,
            Object payload,
            Consumer<HttpHeaders> headersCustomizer
    ) {
        try {
            urlSafetyService.assertRequestAllowed(url);
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Notification destination could not be resolved", exception);
        }
        Integer statusCode = webClient.post()
                .uri(url)
                .headers(headersCustomizer)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .exchangeToMono(response -> response.releaseBody()
                        .thenReturn(response.statusCode().value()))
                .timeout(DELIVERY_TIMEOUT)
                .block();
        return statusCode != null && statusCode >= 200 && statusCode < 300
                ? DispatchResult.sent(statusCode)
                : DispatchResult.failed(statusCode, "Notification endpoint returned a non-success HTTP status");
    }

    private DispatchResult sendOpsgenie(String apiKey, Map<String, Object> payload) {
        String alias = alias(payload);
        if (NotificationEventType.INCIDENT_RESOLVED.name().equals(payload.get("event"))) {
            String encodedAlias = URLEncoder.encode(alias, StandardCharsets.UTF_8);
            return postJson(
                    OPSGENIE_ALERTS_URL + "/" + encodedAlias + "/close?identifierType=alias",
                    Map.of(
                            "user", "APIWatch",
                            "note", text(payload)
                    ),
                    headers -> headers.set("Authorization", "GenieKey " + apiKey)
            );
        }

        return postJson(
                OPSGENIE_ALERTS_URL,
                Map.of(
                        "message", text(payload),
                        "alias", alias,
                        "description", String.valueOf(payload.get("reason")),
                        "priority", "P2",
                        "details", payload
                ),
                headers -> headers.set("Authorization", "GenieKey " + apiKey)
        );
    }

    private DispatchResult sendEmail(String destination, Map<String, Object> payload) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException(
                    "Email notifications require Spring mail configuration"
            );
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailFrom);
        message.setTo(destination);
        message.setSubject("APIWatch " + payload.get("event") + ": " + payload.get("serviceName"));
        message.setText("""
                %s

                Incident ID: %s
                Service ID: %s
                Reason: %s
                Started At: %s
                Resolved At: %s
                Duration Seconds: %s
                """.formatted(
                text(payload),
                payload.get("incidentId"),
                payload.get("serviceId"),
                payload.get("reason"),
                payload.get("startedAt"),
                payload.get("resolvedAt"),
                payload.get("durationSeconds")
        ));
        mailSender.send(message);
        return DispatchResult.sent(null);
    }

    private Map<String, Object> slackPayload(Map<String, Object> payload) {
        return Map.of("text", text(payload));
    }

    private Map<String, Object> discordPayload(Map<String, Object> payload) {
        return Map.of("content", text(payload));
    }

    private Map<String, Object> pagerDutyPayload(String routingKey, Map<String, Object> payload) {
        boolean resolved = NotificationEventType.INCIDENT_RESOLVED.name().equals(payload.get("event"));
        return Map.of(
                "routing_key", routingKey,
                "event_action", resolved ? "resolve" : "trigger",
                "dedup_key", alias(payload),
                "payload", Map.of(
                        "summary", text(payload),
                        "source", String.valueOf(payload.get("serviceName")),
                        "severity", resolved ? "info" : "critical",
                        "timestamp", String.valueOf(payload.get("startedAt")),
                        "custom_details", payload
                )
        );
    }

    private String payloadJson(IncidentNotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(payload(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize notification payload", exception);
        }
    }

    private Map<String, Object> readPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read notification payload", exception);
        }
    }

    private Map<String, Object> payload(IncidentNotificationEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event.eventType().name());
        payload.put("incidentId", event.incidentId());
        payload.put("serviceId", event.serviceId());
        payload.put("serviceName", event.serviceName());
        payload.put("reason", event.reason());
        payload.put("startedAt", formatInstant(event.startedAt()));
        payload.put("resolvedAt", formatInstant(event.resolvedAt()));
        payload.put("durationSeconds", event.durationSeconds());
        payload.put("text", event.eventType() + ": " + event.serviceName() + " - " + event.reason());
        return payload;
    }

    private void saveDelivery(
            IncidentNotificationEvent event,
            NotificationSettingsService.NotificationTarget target,
            NotificationDeliveryStatus status,
            Integer httpStatusCode,
            String errorMessage,
            String payloadJson,
            Instant nextAttemptAt
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setIncidentId(event.incidentId());
        delivery.setServiceId(event.serviceId());
        delivery.setProvider(target.provider());
        delivery.setDestinationDisplay(target.destinationDisplay());
        delivery.setDestinationEncrypted(encryptionService.encrypt(target.destination()));
        delivery.setEventType(event.eventType());
        delivery.setStatus(status);
        delivery.setHttpStatusCode(httpStatusCode);
        delivery.setErrorMessage(errorMessage);
        delivery.setPayloadJson(payloadJson);
        delivery.setNextAttemptAt(nextAttemptAt);
        deliveryRepository.save(delivery);
    }

    private String text(Map<String, Object> payload) {
        Object text = payload.get("text");
        if (text instanceof String value && !value.isBlank()) {
            return value;
        }
        return payload.get("event") + ": " + payload.get("serviceName");
    }

    private String formatInstant(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String alias(Map<String, Object> payload) {
        return "apiwatch-service-" + payload.get("serviceId") + "-incident-" + payload.get("incidentId");
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
                delivery.getProvider(),
                delivery.getDestinationDisplay(),
                delivery.getEventType(),
                delivery.getStatus(),
                delivery.getHttpStatusCode(),
                delivery.getErrorMessage(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getAttemptedAt()
        );
    }

    private record DispatchResult(
            boolean success,
            Integer httpStatusCode,
            String errorMessage
    ) {
        static DispatchResult sent(Integer httpStatusCode) {
            return new DispatchResult(true, httpStatusCode, null);
        }

        static DispatchResult failed(Integer httpStatusCode, String errorMessage) {
            return new DispatchResult(false, httpStatusCode, errorMessage);
        }
    }
}
