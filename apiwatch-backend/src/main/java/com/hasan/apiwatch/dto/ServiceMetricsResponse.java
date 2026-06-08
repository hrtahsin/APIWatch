package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.HealthStatus;

public record ServiceMetricsResponse(
        Long serviceId,
        String serviceName,
        int windowHours,
        double uptimePercentage,
        double averageResponseTimeMs,
        double p95ResponseTimeMs,
        long totalChecks,
        long failedChecks,
        long slowChecks,
        HealthStatus latestStatus
) {
}
