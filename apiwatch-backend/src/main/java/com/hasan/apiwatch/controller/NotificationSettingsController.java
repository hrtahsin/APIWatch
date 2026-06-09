package com.hasan.apiwatch.controller;

import com.hasan.apiwatch.dto.NotificationDeliveryResponse;
import com.hasan.apiwatch.dto.NotificationSettingsResponse;
import com.hasan.apiwatch.dto.UpdateNotificationSettingsRequest;
import com.hasan.apiwatch.service.NotificationSettingsService;
import com.hasan.apiwatch.service.WebhookNotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notification-settings")
public class NotificationSettingsController {

    private final NotificationSettingsService settingsService;
    private final WebhookNotificationService notificationService;

    public NotificationSettingsController(
            NotificationSettingsService settingsService,
            WebhookNotificationService notificationService
    ) {
        this.settingsService = settingsService;
        this.notificationService = notificationService;
    }

    @GetMapping
    NotificationSettingsResponse get() {
        return settingsService.get();
    }

    @PutMapping
    NotificationSettingsResponse update(
            @Valid @RequestBody UpdateNotificationSettingsRequest request
    ) {
        return settingsService.update(request);
    }

    @GetMapping("/deliveries")
    List<NotificationDeliveryResponse> findRecentDeliveries() {
        return notificationService.findRecentDeliveries();
    }
}
