package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.FailureType;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.HttpMethodType;
import com.hasan.apiwatch.enums.RequestAuthType;

import java.time.Instant;
import java.util.List;

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
        Integer lastHttpStatusCode,
        FailureType lastFailureType,
        String lastErrorMessage,
        Instant rateLimitedUntil,
        List<String> customHeaderNames,
        RequestAuthType authType,
        String authHeaderName,
        boolean authConfigured,
        boolean activeIncident,
        Instant createdAt,
        Instant updatedAt
) {
}
