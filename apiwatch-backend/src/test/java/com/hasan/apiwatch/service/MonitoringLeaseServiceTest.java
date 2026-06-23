package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.MonitoringLease;
import com.hasan.apiwatch.repository.MonitoringLeaseRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        when(repository.findByServiceIdForUpdate(7L)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(MonitoringLease.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(leaseService.tryAcquire(7L)).isTrue();

        ArgumentCaptor<MonitoringLease> captor =
                ArgumentCaptor.forClass(MonitoringLease.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getServiceId()).isEqualTo(7L);
        assertThat(captor.getValue().getOwnerId()).isEqualTo("instance-a");
        assertThat(captor.getValue().getLeasedUntil()).isAfter(Instant.now());
    }

    @Test
    void reclaimsExpiredLease() {
        MonitoringLease lease = lease("instance-b", Instant.now().minusSeconds(1));
        when(repository.findByServiceIdForUpdate(7L)).thenReturn(Optional.of(lease));

        assertThat(leaseService.tryAcquire(7L)).isTrue();

        assertThat(lease.getOwnerId()).isEqualTo("instance-a");
        assertThat(lease.getLeasedUntil()).isAfter(Instant.now());
        verify(repository).save(lease);
    }

    @Test
    void refusesActiveLeaseOwnedByAnotherInstance() {
        MonitoringLease lease = lease("instance-b", Instant.now().plusSeconds(60));
        when(repository.findByServiceIdForUpdate(7L)).thenReturn(Optional.of(lease));

        assertThat(leaseService.tryAcquire(7L)).isFalse();

        assertThat(lease.getOwnerId()).isEqualTo("instance-b");
        verify(repository, never()).save(any());
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
