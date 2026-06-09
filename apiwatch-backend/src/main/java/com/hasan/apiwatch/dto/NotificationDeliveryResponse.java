package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;

import java.time.Instant;

public record NotificationDeliveryResponse(
        Long id,
        Long incidentId,
        Long serviceId,
        NotificationEventType eventType,
        NotificationDeliveryStatus status,
        Integer httpStatusCode,
        String errorMessage,
        Instant attemptedAt
) {
}
