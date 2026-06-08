package com.hasan.apiwatch.repository;

import com.hasan.apiwatch.entity.HealthCheck;
import com.hasan.apiwatch.enums.HealthStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {
    Optional<HealthCheck> findTopByMonitoredServiceIdOrderByCheckedAtDesc(Long serviceId);

    List<HealthCheck> findByMonitoredServiceIdOrderByCheckedAtDesc(Long serviceId, Pageable pageable);

    List<HealthCheck> findByMonitoredServiceIdAndCheckedAtGreaterThanEqualOrderByCheckedAtAsc(
            Long serviceId,
            Instant checkedAt
    );

    List<HealthCheck> findByCheckedAtGreaterThanEqual(Instant checkedAt);

    long countByStatus(HealthStatus status);
}
