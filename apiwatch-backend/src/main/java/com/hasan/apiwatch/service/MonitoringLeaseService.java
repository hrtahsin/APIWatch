package com.hasan.apiwatch.service;

import com.hasan.apiwatch.repository.MonitoringLeaseRepository;
import org.springframework.beans.factory.annotation.Value;
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
        return leaseRepository.tryAcquire(serviceId, ownerId, now, leasedUntil) == 1;
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
