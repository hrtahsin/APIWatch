package com.hasan.apiwatch.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean enabled,
        @Size(max = 2048) String webhookUrl,
        Boolean clearWebhook,
        @NotNull @Min(0) @Max(86_400) Integer cooldownSeconds
) {
}
