package com.hasan.apiwatch.dto;

import java.time.Instant;

public record NotificationSettingsResponse(
        boolean enabled,
        boolean webhookConfigured,
        String webhookDisplay,
        int cooldownSeconds,
        Instant updatedAt
) {
}
