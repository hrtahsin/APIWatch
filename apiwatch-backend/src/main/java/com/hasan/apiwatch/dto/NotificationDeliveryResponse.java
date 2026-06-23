package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;
import com.hasan.apiwatch.enums.NotificationProvider;

import java.time.Instant;

public record NotificationDeliveryResponse(
        Long id,
        Long incidentId,
        Long serviceId,
        NotificationProvider provider,
        String destinationDisplay,
        NotificationEventType eventType,
        NotificationDeliveryStatus status,
        Integer httpStatusCode,
        String errorMessage,
        int attemptCount,
        Instant nextAttemptAt,
        Instant attemptedAt
) {
}
