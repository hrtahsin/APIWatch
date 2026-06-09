package com.hasan.apiwatch.service;

import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.repository.HealthCheckRepository;
import com.hasan.apiwatch.repository.IncidentRepository;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class HistoryRetentionService {

    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final int healthCheckDays;
    private final int incidentDays;
    private final int notificationDays;

    public HistoryRetentionService(
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository,
            NotificationDeliveryRepository notificationDeliveryRepository,
            @Value("${apiwatch.retention.health-check-days:90}") int healthCheckDays,
            @Value("${apiwatch.retention.incident-days:365}") int incidentDays,
            @Value("${apiwatch.retention.notification-days:90}") int notificationDays
    ) {
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
        this.notificationDeliveryRepository = notificationDeliveryRepository;
        this.healthCheckDays = healthCheckDays;
        this.incidentDays = incidentDays;
        this.notificationDays = notificationDays;
    }

    @Transactional
    public CleanupResult cleanup(Instant now) {
        long deletedChecks = healthCheckRepository.deleteByCheckedAtBefore(
                now.minus(Duration.ofDays(healthCheckDays))
        );
        long deletedIncidents = incidentRepository.deleteByStatusAndResolvedAtBefore(
                IncidentStatus.RESOLVED,
                now.minus(Duration.ofDays(incidentDays))
        );
        long deletedDeliveries = notificationDeliveryRepository.deleteByAttemptedAtBefore(
                now.minus(Duration.ofDays(notificationDays))
        );
        return new CleanupResult(deletedChecks, deletedIncidents, deletedDeliveries);
    }

    public record CleanupResult(
            long healthChecks,
            long incidents,
            long notificationDeliveries
    ) {
    }
}
