package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.MonitoringLease;
import com.hasan.apiwatch.repository.MonitoringLeaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class MonitoringLeaseService {

    private final MonitoringLeaseRepository leaseRepository;
    private final Duration leaseDuration;
    private final String ownerId;

    public MonitoringLeaseService(
            MonitoringLeaseRepository leaseRepository,
            @Value("${apiwatch.scheduler.lease-seconds:120}") long leaseSeconds,
            @Value("${apiwatch.scheduler.instance-id:}") String configuredInstanceId
    ) {
        this.leaseRepository = leaseRepository;
        this.leaseDuration = Duration.ofSeconds(Math.max(leaseSeconds, 5));
        this.ownerId = resolveOwnerId(configuredInstanceId);
    }

    @Transactional
    public boolean tryAcquire(Long serviceId) {
        Instant now = Instant.now();
        Instant leasedUntil = now.plus(leaseDuration);
        return leaseRepository.findByServiceIdForUpdate(serviceId)
                .map(existing -> acquireExisting(existing, now, leasedUntil))
                .orElseGet(() -> acquireNew(serviceId, leasedUntil));
    }

    @Transactional
    public void release(Long serviceId) {
        leaseRepository.findByServiceIdForUpdate(serviceId)
                .filter(existing -> ownerId.equals(existing.getOwnerId()))
                .ifPresent(existing -> {
                    existing.setLeasedUntil(Instant.now());
                    leaseRepository.save(existing);
                });
    }

    String ownerId() {
        return ownerId;
    }

    private boolean acquireExisting(
            MonitoringLease existing,
            Instant now,
            Instant leasedUntil
    ) {
        boolean heldByAnotherInstance = existing.getLeasedUntil().isAfter(now)
                && !ownerId.equals(existing.getOwnerId());
        if (heldByAnotherInstance) {
            return false;
        }
        existing.setOwnerId(ownerId);
        existing.setLeasedUntil(leasedUntil);
        leaseRepository.save(existing);
        return true;
    }

    private boolean acquireNew(Long serviceId, Instant leasedUntil) {
        try {
            MonitoringLease lease = new MonitoringLease();
            lease.setServiceId(serviceId);
            lease.setOwnerId(ownerId);
            lease.setLeasedUntil(leasedUntil);
            leaseRepository.saveAndFlush(lease);
            return true;
        } catch (DataIntegrityViolationException ignored) {
            return false;
        }
    }

    private String resolveOwnerId(String configuredInstanceId) {
        if (configuredInstanceId != null && !configuredInstanceId.isBlank()) {
            return configuredInstanceId.trim();
        }
        return hostname() + "-" + UUID.randomUUID();
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            return "apiwatch";
        }
    }
}
