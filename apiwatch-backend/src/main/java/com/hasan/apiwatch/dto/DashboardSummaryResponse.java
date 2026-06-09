package com.hasan.apiwatch.dto;

public record DashboardSummaryResponse(
        long totalServices,
        long upServices,
        long slowServices,
        long downServices,
        long rateLimitedServices,
        long unknownServices,
        long activeIncidents,
        double averageResponseTimeMs,
        double overallUptimePercentage
) {
}
