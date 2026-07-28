package com.hasan.apiwatch;

import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.entity.MonitoredService;
import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.repository.MonitoringLeaseRepository;
import com.hasan.apiwatch.service.NotificationDeliveryClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresConcurrencyIT extends PostgresIntegrationTest {

    @Autowired
    private MonitoringLeaseRepository leaseRepository;

    @Autowired
    private NotificationDeliveryClaimService claimService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void grantsAServiceLeaseToOnlyOneCompetingReplica() throws Exception {
        MonitoredService service = saveService("Lease Race API");
        Instant now = Instant.now();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() ->
                    acquireLeaseAfter(start, service.getId(), "replica-a", now)
            );
            Future<Integer> second = executor.submit(() ->
                    acquireLeaseAfter(start, service.getId(), "replica-b", now)
            );
            start.countDown();

            assertThat(first.get() + second.get()).isEqualTo(1);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monitoring_leases WHERE service_id = ?",
                Long.class,
                service.getId()
        )).isEqualTo(1);
    }

    @Test
    void reclaimsExpiredLeaseButProtectsAnActiveLease() {
        MonitoredService service = saveService("Lease Recovery API");
        Instant now = Instant.now();

        assertThat(acquireLease(service.getId(), "replica-a", now)).isEqualTo(1);
        assertThat(acquireLease(service.getId(), "replica-b", now.plusSeconds(1))).isZero();

        jdbcTemplate.update(
                "UPDATE monitoring_leases SET leased_until = ? WHERE service_id = ?",
                Timestamp.from(now.minusSeconds(1)),
                service.getId()
        );

        assertThat(acquireLease(service.getId(), "replica-b", now.plusSeconds(2))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT owner_id FROM monitoring_leases WHERE service_id = ?",
                String.class,
                service.getId()
        )).isEqualTo("replica-b");
    }

    @Test
    void claimsEachDueNotificationOnlyOnceUnderConcurrency() throws Exception {
        MonitoredService service = saveService("Claim Race API");
        Incident incident = saveIncident(
                service,
                IncidentStatus.ACTIVE,
                Instant.now().minusSeconds(30),
                null
        );
        NotificationDelivery delivery = saveDelivery(
                incident,
                NotificationDeliveryStatus.PENDING,
                "event:claim-race"
        );
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<List<Long>> first = executor.submit(() -> claimAfter(start));
            Future<List<Long>> second = executor.submit(() -> claimAfter(start));
            start.countDown();

            assertThat(first.get().stream().count() + second.get().stream().count()).isEqualTo(1);
        }

        NotificationDelivery claimed = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(NotificationDeliveryStatus.PROCESSING);
        assertThat(claimed.getClaimedBy()).isEqualTo("postgres-it-notifications");
        assertThat(claimed.getClaimedUntil()).isAfter(Instant.now());
    }

    @Test
    void recoversOnlyExpiredNotificationClaims() {
        MonitoredService service = saveService("Claim Recovery API");
        Incident incident = saveIncident(
                service,
                IncidentStatus.ACTIVE,
                Instant.now().minusSeconds(30),
                null
        );
        NotificationDelivery expired = saveDelivery(
                incident,
                NotificationDeliveryStatus.PROCESSING,
                "event:expired-claim"
        );
        NotificationDelivery active = saveDelivery(
                incident,
                NotificationDeliveryStatus.PROCESSING,
                "event:active-claim"
        );
        jdbcTemplate.update(
                "UPDATE notification_deliveries SET claimed_until = ?, claimed_by = 'dead-worker' WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                expired.getId()
        );
        jdbcTemplate.update(
                "UPDATE notification_deliveries SET claimed_until = ?, claimed_by = 'live-worker' WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(60)),
                active.getId()
        );

        assertThat(claimService.claimDue(10)).containsExactly(expired.getId());
        assertThat(deliveryRepository.findById(active.getId()).orElseThrow().getClaimedBy())
                .isEqualTo("live-worker");
    }

    @Test
    void allowsOnlyOneSimultaneousActiveIncidentInsert() throws Exception {
        MonitoredService service = saveService("Incident Race API");
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() ->
                    insertActiveIncidentAfter(start, service.getId(), "replica-a")
            );
            Future<Boolean> second = executor.submit(() ->
                    insertActiveIncidentAfter(start, service.getId(), "replica-b")
            );
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(incidentRepository.countByStatus(IncidentStatus.ACTIVE)).isEqualTo(1);
    }

    private int acquireLeaseAfter(
            CountDownLatch start,
            Long serviceId,
            String owner,
            Instant now
    ) throws InterruptedException {
        start.await();
        return acquireLease(serviceId, owner, now);
    }

    private int acquireLease(Long serviceId, String owner, Instant now) {
        return transactionTemplate.execute(status ->
                leaseRepository.tryAcquire(
                        serviceId,
                        owner,
                        now,
                        now.plusSeconds(30)
                )
        );
    }

    private List<Long> claimAfter(CountDownLatch start) throws InterruptedException {
        start.await();
        return claimService.claimDue(10);
    }

    private boolean insertActiveIncidentAfter(
            CountDownLatch start,
            Long serviceId,
            String reason
    ) throws InterruptedException {
        start.await();
        try {
            jdbcTemplate.update("""
                    INSERT INTO incidents (
                        service_id,
                        status,
                        reason,
                        started_at,
                        created_at,
                        updated_at
                    )
                    VALUES (?, 'ACTIVE', ?, NOW(), NOW(), NOW())
                    """, serviceId, reason);
            return true;
        } catch (DataIntegrityViolationException ignored) {
            return false;
        }
    }
}
