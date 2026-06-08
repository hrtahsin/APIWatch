package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.HealthStatus;

import java.time.Instant;

public record HealthCheckResponse(
        Long id,
        Long serviceId,
        HealthStatus status,
        Integer httpStatusCode,
        Long responseTimeMs,
        String errorMessage,
        Instant checkedAt
) {
}
