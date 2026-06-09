package com.hasan.apiwatch.service;

import com.hasan.apiwatch.dto.NotificationSettingsResponse;
import com.hasan.apiwatch.dto.UpdateNotificationSettingsRequest;
import com.hasan.apiwatch.entity.NotificationSettings;
import com.hasan.apiwatch.exception.BadRequestException;
import com.hasan.apiwatch.repository.NotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository repository;
    private final SecretEncryptionService encryptionService;
    private final UrlSafetyService urlSafetyService;

    public NotificationSettingsService(
            NotificationSettingsRepository repository,
            SecretEncryptionService encryptionService,
            UrlSafetyService urlSafetyService
    ) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.urlSafetyService = urlSafetyService;
    }

    @Transactional
    public NotificationSettingsResponse get() {
        return toResponse(getOrCreate());
    }

    @Transactional
    public NotificationSettingsResponse update(UpdateNotificationSettingsRequest request) {
        NotificationSettings settings = getOrCreate();
        if (Boolean.TRUE.equals(request.clearWebhook())) {
            settings.setWebhookUrlEncrypted(null);
        } else if (request.webhookUrl() != null && !request.webhookUrl().isBlank()) {
            String webhookUrl = urlSafetyService.validateConfiguration(request.webhookUrl());
            settings.setWebhookUrlEncrypted(encryptionService.encrypt(webhookUrl));
        }

        if (request.enabled() && settings.getWebhookUrlEncrypted() == null) {
            throw new BadRequestException("A webhook URL is required before notifications can be enabled");
        }
        settings.setEnabled(request.enabled());
        settings.setCooldownSeconds(request.cooldownSeconds());
        return toResponse(repository.save(settings));
    }

    @Transactional(readOnly = true)
    public Optional<NotificationTarget> loadTarget() {
        return repository.findFirstByOrderByIdAsc()
                .filter(NotificationSettings::isEnabled)
                .filter(settings -> settings.getWebhookUrlEncrypted() != null)
                .map(settings -> new NotificationTarget(
                        encryptionService.decrypt(settings.getWebhookUrlEncrypted()),
                        settings.getCooldownSeconds()
                ));
    }

    private NotificationSettings getOrCreate() {
        return repository.findFirstByOrderByIdAsc()
                .orElseGet(() -> repository.save(new NotificationSettings()));
    }

    private NotificationSettingsResponse toResponse(NotificationSettings settings) {
        String display = null;
        if (settings.getWebhookUrlEncrypted() != null) {
            String webhookUrl = encryptionService.decrypt(settings.getWebhookUrlEncrypted());
            try {
                URI uri = new URI(webhookUrl);
                display = uri.getScheme() + "://" + uri.getHost() + "/****";
            } catch (URISyntaxException ignored) {
                display = "Configured webhook";
            }
        }
        return new NotificationSettingsResponse(
                settings.isEnabled(),
                settings.getWebhookUrlEncrypted() != null,
                display,
                settings.getCooldownSeconds(),
                settings.getUpdatedAt()
        );
    }

    public record NotificationTarget(String webhookUrl, int cooldownSeconds) {
    }
}
