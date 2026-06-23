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
        String ownerName,
        String teamName,
        List<String> tags,
        HttpMethodType method,
        int expectedStatusCode,
        int expectedStatusMin,
        int expectedStatusMax,
        int timeoutMs,
        int checkIntervalSeconds,
        String responseBodyContains,
        int failureThreshold,
        boolean notifyOnIncidentOpen,
        boolean notifyOnIncidentResolve,
        int notificationEscalationMinutes,
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
