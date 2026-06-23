package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.NotificationProvider;

import java.time.Instant;

public record NotificationSettingsResponse(
        boolean enabled,
        NotificationProvider provider,
        boolean destinationConfigured,
        String destinationDisplay,
        boolean webhookConfigured,
        String webhookDisplay,
        int cooldownSeconds,
        int escalationMinutes,
        Instant updatedAt
) {
}
