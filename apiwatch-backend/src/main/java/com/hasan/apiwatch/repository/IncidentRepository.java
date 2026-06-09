package com.hasan.apiwatch.repository;

import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.enums.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findFirstByMonitoredServiceIdAndStatus(Long serviceId, IncidentStatus status);

    List<Incident> findAllByOrderByStartedAtDesc();

    List<Incident> findByStatusOrderByStartedAtDesc(IncidentStatus status);

    Page<Incident> findByStatusOrderByStartedAtDesc(
            IncidentStatus status,
            Pageable pageable
    );

    List<Incident> findByMonitoredServiceIdOrderByStartedAtDesc(Long serviceId);

    Page<Incident> findByMonitoredServiceIdOrderByStartedAtDesc(
            Long serviceId,
            Pageable pageable
    );

    List<Incident> findByMonitoredServiceIdAndStatusOrderByStartedAtDesc(
            Long serviceId,
            IncidentStatus status
    );

    Page<Incident> findByMonitoredServiceIdAndStatusOrderByStartedAtDesc(
            Long serviceId,
            IncidentStatus status,
            Pageable pageable
    );

    long countByStatus(IncidentStatus status);

    long deleteByStatusAndResolvedAtBefore(IncidentStatus status, Instant cutoff);

    long deleteByMonitoredServiceId(Long serviceId);
}
