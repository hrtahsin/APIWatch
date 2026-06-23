package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.NotificationSettingsResponse;
import com.hasan.apiwatch.dto.UpdateNotificationSettingsRequest;
import com.hasan.apiwatch.entity.NotificationSettings;
import com.hasan.apiwatch.enums.AuditAction;
import com.hasan.apiwatch.enums.NotificationProvider;
import com.hasan.apiwatch.exception.BadRequestException;
import com.hasan.apiwatch.repository.NotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class NotificationSettingsService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    private final NotificationSettingsRepository repository;
    private final SecretEncryptionService encryptionService;
    private final UrlSafetyService urlSafetyService;
    private final AuditLogService auditLogService;

    public NotificationSettingsService(
            NotificationSettingsRepository repository,
            SecretEncryptionService encryptionService,
            UrlSafetyService urlSafetyService,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.urlSafetyService = urlSafetyService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public NotificationSettingsResponse get() {
        return toResponse(getOrCreate());
    }

    @Transactional
    public NotificationSettingsResponse update(UpdateNotificationSettingsRequest request) {
        NotificationSettings settings = getOrCreate();
        NotificationProvider provider = request.provider() == null
                ? settings.getProvider()
                : request.provider();
        settings.setProvider(provider);

        if (Boolean.TRUE.equals(request.clearDestination())
                || Boolean.TRUE.equals(request.clearWebhook())) {
            settings.setDestinationEncrypted(null);
            settings.setWebhookUrlEncrypted(null);
        } else {
            String destination = firstNonBlank(request.destination(), request.webhookUrl());
            if (destination != null) {
                String normalizedDestination = validateDestination(provider, destination);
                String encryptedDestination = encryptionService.encrypt(normalizedDestination);
                settings.setDestinationEncrypted(encryptedDestination);
                settings.setWebhookUrlEncrypted(
                        usesUrlDestination(provider) ? encryptedDestination : null
                );
            }
        }

        if (request.enabled() && settings.getDestinationEncrypted() == null) {
            throw new BadRequestException(
                    "A notification destination is required before notifications can be enabled"
            );
        }
        settings.setEnabled(request.enabled());
        settings.setCooldownSeconds(request.cooldownSeconds());
        settings.setEscalationMinutes(
                request.escalationMinutes() == null
                        ? settings.getEscalationMinutes()
                        : request.escalationMinutes()
        );
        NotificationSettings saved = repository.save(settings);
        auditLogService.record(
                AuditAction.NOTIFICATION_SETTINGS_UPDATED,
                "NOTIFICATION_SETTINGS",
                saved.getId(),
                "Notification settings",
                "Updated notification settings: enabled=%s, provider=%s, destinationConfigured=%s, cooldownSeconds=%d, escalationMinutes=%d"
                        .formatted(
                                saved.isEnabled(),
                                saved.getProvider(),
                                saved.getDestinationEncrypted() != null,
                                saved.getCooldownSeconds(),
                                saved.getEscalationMinutes()
                        )
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<NotificationTarget> loadTarget() {
        return repository.findFirstByOrderByIdAsc()
                .filter(NotificationSettings::isEnabled)
                .filter(settings -> destinationEncrypted(settings) != null)
                .map(settings -> new NotificationTarget(
                        settings.getProvider(),
                        encryptionService.decrypt(destinationEncrypted(settings)),
                        displayDestination(
                                settings.getProvider(),
                                encryptionService.decrypt(destinationEncrypted(settings))
                        ),
                        settings.getCooldownSeconds(),
                        settings.getEscalationMinutes()
                ));
    }

    private NotificationSettings getOrCreate() {
        return repository.findFirstByOrderByIdAsc()
                .orElseGet(() -> repository.save(new NotificationSettings()));
    }

    private NotificationSettingsResponse toResponse(NotificationSettings settings) {
        String encryptedDestination = destinationEncrypted(settings);
        String display = encryptedDestination == null
                ? null
                : displayDestination(
                        settings.getProvider(),
                        encryptionService.decrypt(encryptedDestination)
                );
        boolean destinationConfigured = encryptedDestination != null;
        return new NotificationSettingsResponse(
                settings.isEnabled(),
                settings.getProvider(),
                destinationConfigured,
                display,
                destinationConfigured && usesUrlDestination(settings.getProvider()),
                display,
                settings.getCooldownSeconds(),
                settings.getEscalationMinutes(),
                settings.getUpdatedAt()
        );
    }

    private String validateDestination(NotificationProvider provider, String destination) {
        String normalized = destination.trim();
        return switch (provider) {
            case WEBHOOK, SLACK, DISCORD -> urlSafetyService.validateConfiguration(normalized);
            case EMAIL -> validateEmail(normalized);
            case PAGERDUTY, OPSGENIE -> {
                if (normalized.isBlank()) {
                    throw new BadRequestException("Integration key cannot be blank");
                }
                yield normalized;
            }
        };
    }

    private String validateEmail(String destination) {
        if (!EMAIL_PATTERN.matcher(destination).matches()) {
            throw new BadRequestException("Email notification destination must be a valid address");
        }
        return destination.toLowerCase(Locale.ROOT);
    }

    private String displayDestination(NotificationProvider provider, String destination) {
        return switch (provider) {
            case EMAIL -> maskEmail(destination);
            case PAGERDUTY -> "Configured PagerDuty integration key";
            case OPSGENIE -> "Configured Opsgenie API key";
            case WEBHOOK, SLACK, DISCORD -> maskUrl(destination);
        };
    }

    private String maskUrl(String destination) {
        try {
            URI uri = new URI(destination);
            return uri.getScheme() + "://" + uri.getHost() + "/****";
        } catch (URISyntaxException ignored) {
            return "Configured webhook";
        }
    }

    private String maskEmail(String destination) {
        int at = destination.indexOf('@');
        if (at <= 1) {
            return "****" + destination.substring(Math.max(at, 0));
        }
        return destination.charAt(0) + "****" + destination.substring(at);
    }

    private String destinationEncrypted(NotificationSettings settings) {
        if (settings.getDestinationEncrypted() != null) {
            return settings.getDestinationEncrypted();
        }
        return settings.getWebhookUrlEncrypted();
    }

    private boolean usesUrlDestination(NotificationProvider provider) {
        return provider == NotificationProvider.WEBHOOK
                || provider == NotificationProvider.SLACK
                || provider == NotificationProvider.DISCORD;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    public record NotificationTarget(
            NotificationProvider provider,
            String destination,
            String destinationDisplay,
            int cooldownSeconds,
            int escalationMinutes
    ) {
    }
}
