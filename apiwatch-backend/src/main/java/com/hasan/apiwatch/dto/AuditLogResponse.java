package com.hasan.apiwatch.dto;

import com.hasan.apiwatch.enums.AuditAction;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String actorUsername,
        AuditAction action,
        String targetType,
        Long targetId,
        String targetName,
        String details,
        Instant createdAt
) {
}
