package com.hasan.apiwatch.repository;

import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.enums.NotificationEventType;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.domain.Pageable;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT delivery
            FROM NotificationDelivery delivery
            WHERE (
                delivery.status = :pending
                AND delivery.nextAttemptAt <= :now
            ) OR (
                delivery.status = :processing
                AND delivery.claimedUntil <= :now
            )
            ORDER BY COALESCE(delivery.nextAttemptAt, delivery.claimedUntil) ASC
            """)
    List<NotificationDelivery> findClaimable(
            NotificationDeliveryStatus pending,
            NotificationDeliveryStatus processing,
            Instant now,
            Pageable pageable
    );

    boolean existsByEventKey(String eventKey);

    long deleteByAttemptedAtBefore(Instant cutoff);

    long deleteByServiceId(Long serviceId);
}
