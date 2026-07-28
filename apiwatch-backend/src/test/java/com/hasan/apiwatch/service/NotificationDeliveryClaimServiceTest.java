package com.hasan.apiwatch.service;

import com.hasan.apiwatch.entity.NotificationDelivery;
import com.hasan.apiwatch.enums.NotificationDeliveryStatus;
import com.hasan.apiwatch.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryClaimServiceTest {

    @Test
    void claimsPendingAndExpiredDeliveriesForOneWorker() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        NotificationDelivery pending = delivery(1L, NotificationDeliveryStatus.PENDING);
        NotificationDelivery expired = delivery(2L, NotificationDeliveryStatus.PROCESSING);
        when(repository.findClaimable(
                eq(NotificationDeliveryStatus.PENDING),
                eq(NotificationDeliveryStatus.PROCESSING),
                any(Instant.class),
                any(Pageable.class)
        )).thenReturn(List.of(pending, expired));
        NotificationDeliveryClaimService service =
                new NotificationDeliveryClaimService(repository, 30, "worker-a");

        List<Long> claimed = service.claimDue(25);

        assertThat(claimed).containsExactly(1L, 2L);
        assertThat(pending.getStatus()).isEqualTo(NotificationDeliveryStatus.PROCESSING);
        assertThat(expired.getStatus()).isEqualTo(NotificationDeliveryStatus.PROCESSING);
        assertThat(pending.getClaimedBy()).isEqualTo("worker-a");
        assertThat(expired.getClaimedBy()).isEqualTo("worker-a");
        assertThat(pending.getClaimedUntil()).isAfter(Instant.now());
        assertThat(pending.getNextAttemptAt()).isNull();
        verify(repository).saveAll(List.of(pending, expired));
    }

    private NotificationDelivery delivery(
            Long id,
            NotificationDeliveryStatus status
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        ReflectionTestUtils.setField(delivery, "id", id);
        delivery.setStatus(status);
        delivery.setNextAttemptAt(Instant.now().minusSeconds(5));
        delivery.setClaimedUntil(Instant.now().minusSeconds(5));
        return delivery;
    }
}
