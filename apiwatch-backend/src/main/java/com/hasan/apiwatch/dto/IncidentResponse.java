package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.IncidentStatus;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        Long serviceId,
        String serviceName,
        IncidentStatus status,
        String reason,
        Instant startedAt,
        Instant resolvedAt,
        Long durationSeconds,
        Instant createdAt,
        Instant updatedAt
) {
}
