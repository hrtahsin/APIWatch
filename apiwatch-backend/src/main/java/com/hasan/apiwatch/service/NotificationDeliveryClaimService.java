package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationDeliveryClaimService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final Duration claimDuration;
    private final String ownerId;

    public NotificationDeliveryClaimService(
            NotificationDeliveryRepository deliveryRepository,
            @Value("${apiwatch.notifications.claim-seconds:30}") long claimSeconds,
            @Value("${apiwatch.notifications.instance-id:}") String configuredInstanceId
    ) {
        this.deliveryRepository = deliveryRepository;
        this.claimDuration = Duration.ofSeconds(Math.max(claimSeconds, 15));
        this.ownerId = resolveOwnerId(configuredInstanceId);
    }

    @Transactional
    public List<Long> claimDue(int limit) {
        Instant now = Instant.now();
        Instant claimedUntil = now.plus(claimDuration);
        List<NotificationDelivery> deliveries = deliveryRepository.findClaimable(
                NotificationDeliveryStatus.PENDING,
                NotificationDeliveryStatus.PROCESSING,
                now,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 100))
        );
        deliveries.forEach(delivery -> {
            delivery.setStatus(NotificationDeliveryStatus.PROCESSING);
            delivery.setClaimedBy(ownerId);
            delivery.setClaimedUntil(claimedUntil);
            delivery.setNextAttemptAt(null);
        });
        deliveryRepository.saveAll(deliveries);
        return deliveries.stream().map(NotificationDelivery::getId).toList();
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
