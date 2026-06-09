package com.hasan.apiwatch.event;

import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.enums.NotificationEventType;

import java.time.Instant;

public record IncidentNotificationEvent(
        NotificationEventType eventType,
        Long incidentId,
        Long serviceId,
        String serviceName,
        String reason,
        Instant startedAt,
        Instant resolvedAt,
        Long durationSeconds
) {

    public static IncidentNotificationEvent opened(Incident incident) {
        return from(incident, NotificationEventType.INCIDENT_OPENED);
    }

    public static IncidentNotificationEvent resolved(Incident incident) {
        return from(incident, NotificationEventType.INCIDENT_RESOLVED);
    }

    private static IncidentNotificationEvent from(
            Incident incident,
            NotificationEventType eventType
    ) {
        return new IncidentNotificationEvent(
                eventType,
                incident.getId(),
                incident.getMonitoredService().getId(),
                incident.getMonitoredService().getName(),
                incident.getReason(),
                incident.getStartedAt(),
                incident.getResolvedAt(),
                incident.getDurationSeconds()
        );
    }
}
