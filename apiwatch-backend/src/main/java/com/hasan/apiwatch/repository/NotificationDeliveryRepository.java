package com.hasan.apiwatch.repository;

import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository
        extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery>
    findFirstByServiceIdAndEventTypeAndStatusOrderByAttemptedAtDesc(
            Long serviceId,
            NotificationEventType eventType,
            NotificationDeliveryStatus status
    );

    List<NotificationDelivery> findTop50ByOrderByAttemptedAtDesc();

    List<NotificationDelivery> findTop25ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            NotificationDeliveryStatus status,
            Instant nextAttemptAt
    );

    long deleteByAttemptedAtBefore(Instant cutoff);

    long deleteByServiceId(Long serviceId);
}
