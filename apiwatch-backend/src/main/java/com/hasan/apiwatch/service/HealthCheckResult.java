package com.hasan.apiwatch.service;

import com.hasan.apiwatch.enums.FailureType;
import com.hasan.apiwatch.enums.HealthStatus;

import java.time.Instant;

record HealthCheckResult(
        HealthStatus status,
        Integer httpStatusCode,
        long responseTimeMs,
        FailureType failureType,
        String errorMessage,
        Long retryAfterSeconds,
        Long rateLimitRemaining,
        Instant rateLimitResetAt,
        Instant rateLimitedUntil,
        boolean clearRateLimit
) {
}
