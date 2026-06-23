package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.NotificationProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean enabled,
        NotificationProvider provider,
        @Size(max = 4096) String destination,
        Boolean clearDestination,
        @Size(max = 2048) String webhookUrl,
        Boolean clearWebhook,
        @NotNull @Min(0) @Max(86_400) Integer cooldownSeconds,
        @Min(0) @Max(10_080) Integer escalationMinutes
) {
}
