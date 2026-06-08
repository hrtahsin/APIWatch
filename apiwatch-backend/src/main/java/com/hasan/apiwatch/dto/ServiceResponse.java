package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.HttpMethodType;

import java.time.Instant;

public record ServiceResponse(
        Long id,
        String name,
        String url,
        HttpMethodType method,
        int expectedStatusCode,
        int timeoutMs,
        int failureThreshold,
        boolean active,
        HealthStatus currentStatus,
        Instant lastCheckedAt,
        Long lastResponseTimeMs,
        boolean activeIncident,
        Instant createdAt,
        Instant updatedAt
) {
}
