package com.hasan.apiwatch;

import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.service.HistoryRetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgresRepositoryIT extends PostgresIntegrationTest {

    @Autowired
    private HistoryRetentionService retentionService;

    @Test
    void persistsUtcTimestampsAndPagesRepositoryHistory() {
        MonitoredService service = saveService("Paged API");
        Instant now = Instant.parse("2026-07-28T01:00:00Z");
        HealthCheck oldest = saveCheck(service, HealthStatus.DOWN, now.minusSeconds(120));
        HealthCheck middle = saveCheck(service, HealthStatus.UP, now.minusSeconds(60));
        HealthCheck newest = saveCheck(service, HealthStatus.UP, now);

        var page = healthCheckRepository.findByMonitoredServiceIdOrderByCheckedAtDesc(
                service.getId(),
                PageRequest.of(0, 2)
        );

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .extracting(HealthCheck::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(oldest.getCheckedAt()).isEqualTo(now.minusSeconds(120));
        assertThat(service.getCreatedAt()).isNotNull();
        assertThat(service.getUpdatedAt()).isNotNull();
    }

    @Test
    void filtersIncidentsWithPostgresPaging() {
        MonitoredService first = saveService("First API");
        MonitoredService second = saveService("Second API");
        Instant now = Instant.parse("2026-07-28T02:00:00Z");
        Incident active = saveIncident(first, IncidentStatus.ACTIVE, now, null);
        saveIncident(
                second,
                IncidentStatus.RESOLVED,
                now.minusSeconds(300),
                now.minusSeconds(60)
        );

        var page = incidentRepository.findByStatusOrderByStartedAtDesc(
                IncidentStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Incident::getId).containsExactly(active.getId());
    }

    @Test
    void preventsMoreThanOneActiveIncidentPerService() {
        MonitoredService service = saveService("Unique Incident API");
        Instant now = Instant.now();
        saveIncident(service, IncidentStatus.ACTIVE, now.minusSeconds(30), null);

        assertThatThrownBy(() ->
                saveIncident(service, IncidentStatus.ACTIVE, now, null)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsDuplicateNotificationEventKeys() {
        MonitoredService service = saveService("Unique Event API");
        Incident incident = saveIncident(service, IncidentStatus.ACTIVE, Instant.now(), null);
        saveDelivery(incident, NotificationDeliveryStatus.PENDING, "event:unique");

        assertThatThrownBy(() ->
                saveDelivery(incident, NotificationDeliveryStatus.PENDING, "event:unique")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cascadesServiceDeletionAcrossOperationalHistory() {
        MonitoredService service = saveService("Cascade API");
        saveCheck(service, HealthStatus.DOWN, Instant.now());
        Incident incident = saveIncident(
                service,
                IncidentStatus.ACTIVE,
                Instant.now().minusSeconds(30),
                null
        );
        saveDelivery(incident, NotificationDeliveryStatus.PENDING, "event:cascade");
        jdbcTemplate.update("""
                INSERT INTO monitoring_leases (
                    service_id,
                    owner_id,
                    leased_until,
                    created_at,
                    updated_at
                )
                VALUES (?, 'cascade-test', NOW() + INTERVAL '1 minute', NOW(), NOW())
                """, service.getId());

        serviceRepository.deleteById(service.getId());
        serviceRepository.flush();

        assertThat(healthCheckRepository.count()).isZero();
        assertThat(incidentRepository.count()).isZero();
        assertThat(deliveryRepository.count()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monitoring_leases",
                Long.class
        )).isZero();
    }

    @Test
    void rejectsLateResultAfterItsServiceWasDeleted() {
        MonitoredService deletedService = saveService("Deleted During Check API");
        serviceRepository.deleteById(deletedService.getId());
        serviceRepository.flush();

        HealthCheck lateResult = new HealthCheck();
        lateResult.setMonitoredService(deletedService);
        lateResult.setStatus(HealthStatus.UP);
        lateResult.setHttpStatusCode(200);
        lateResult.setResponseTimeMs(20L);

        assertThatThrownBy(() -> healthCheckRepository.saveAndFlush(lateResult))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removesOnlyHistoryOutsideConfiguredRetentionWindows() {
        MonitoredService service = saveService("Retention API");
        Instant now = Instant.parse("2026-07-28T03:00:00Z");
        HealthCheck oldCheck = saveCheck(service, HealthStatus.UP, now.minusSeconds(31L * 86400));
        HealthCheck recentCheck = saveCheck(service, HealthStatus.UP, now.minusSeconds(86400));
        Incident oldIncident = saveIncident(
                service,
                IncidentStatus.RESOLVED,
                now.minusSeconds(40L * 86400),
                now.minusSeconds(31L * 86400)
        );
        Incident recentIncident = saveIncident(
                service,
                IncidentStatus.RESOLVED,
                now.minusSeconds(2L * 86400),
                now.minusSeconds(86400)
        );
        Incident activeIncident = saveIncident(
                service,
                IncidentStatus.ACTIVE,
                now.minusSeconds(3600),
                null
        );
        NotificationDelivery oldDelivery = saveDelivery(
                activeIncident,
                NotificationDeliveryStatus.SENT,
                "event:old"
        );
        NotificationDelivery recentDelivery = saveDelivery(
                activeIncident,
                NotificationDeliveryStatus.SENT,
                "event:recent"
        );
        jdbcTemplate.update(
                "UPDATE notification_deliveries SET attempted_at = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(31L * 86400)),
                oldDelivery.getId()
        );
        jdbcTemplate.update(
                "UPDATE notification_deliveries SET attempted_at = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(86400)),
                recentDelivery.getId()
        );

        HistoryRetentionService.CleanupResult result = retentionService.cleanup(now);

        assertThat(result.healthChecks()).isEqualTo(1);
        assertThat(result.incidents()).isEqualTo(1);
        assertThat(result.notificationDeliveries()).isEqualTo(1);
        assertThat(healthCheckRepository.findById(oldCheck.getId())).isEmpty();
        assertThat(healthCheckRepository.findById(recentCheck.getId())).isPresent();
        assertThat(incidentRepository.findById(oldIncident.getId())).isEmpty();
        assertThat(incidentRepository.findById(recentIncident.getId())).isPresent();
        assertThat(incidentRepository.findById(activeIncident.getId())).isPresent();
        assertThat(deliveryRepository.findById(oldDelivery.getId())).isEmpty();
        assertThat(deliveryRepository.findById(recentDelivery.getId())).isPresent();
    }
}
