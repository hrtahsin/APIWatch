package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.MonitoringLease;
import com.hasan.apiwatch.repository.MonitoringLeaseRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringLeaseServiceTest {

    private final MonitoringLeaseRepository repository = mock(MonitoringLeaseRepository.class);
    private final MonitoringLeaseService leaseService =
            new MonitoringLeaseService(repository, 30, "instance-a");

    @Test
    void createsLeaseWhenNoLeaseExists() {
        when(repository.tryAcquire(eq(7L), eq("instance-a"), any(), any()))
                .thenReturn(1);

        assertThat(leaseService.tryAcquire(7L)).isTrue();

        verify(repository).tryAcquire(eq(7L), eq("instance-a"), any(), any());
    }

    @Test
    void reclaimsExpiredLease() {
        when(repository.tryAcquire(eq(7L), eq("instance-a"), any(), any()))
                .thenReturn(1);

        assertThat(leaseService.tryAcquire(7L)).isTrue();
    }

    @Test
    void refusesActiveLeaseOwnedByAnotherInstance() {
        when(repository.tryAcquire(eq(7L), eq("instance-a"), any(), any()))
                .thenReturn(0);

        assertThat(leaseService.tryAcquire(7L)).isFalse();
    }

    @Test
    void releasesLeaseOwnedByThisInstance() {
        MonitoringLease lease = lease("instance-a", Instant.now().plusSeconds(60));
        when(repository.findByServiceIdForUpdate(7L)).thenReturn(Optional.of(lease));

        leaseService.release(7L);

        assertThat(lease.getLeasedUntil()).isBeforeOrEqualTo(Instant.now());
        verify(repository).save(lease);
    }

    @Test
    void doesNotReleaseLeaseOwnedByAnotherInstance() {
        MonitoringLease lease = lease("instance-b", Instant.now().plusSeconds(60));
        when(repository.findByServiceIdForUpdate(7L)).thenReturn(Optional.of(lease));

        leaseService.release(7L);

        verify(repository, never()).save(any());
    }

    private MonitoringLease lease(String ownerId, Instant leasedUntil) {
        MonitoringLease lease = new MonitoringLease();
        lease.setServiceId(7L);
        lease.setOwnerId(ownerId);
        lease.setLeasedUntil(leasedUntil);
        return lease;
    }
}
