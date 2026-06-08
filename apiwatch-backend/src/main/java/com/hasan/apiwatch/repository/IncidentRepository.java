package com.hasan.apiwatch.repository;

import com.hasan.apiwatch.entity.Incident;
import com.hasan.apiwatch.enums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Optional<Incident> findFirstByMonitoredServiceIdAndStatus(Long serviceId, IncidentStatus status);

    List<Incident> findAllByOrderByStartedAtDesc();

    List<Incident> findByStatusOrderByStartedAtDesc(IncidentStatus status);

    List<Incident> findByMonitoredServiceIdOrderByStartedAtDesc(Long serviceId);

    List<Incident> findByMonitoredServiceIdAndStatusOrderByStartedAtDesc(
            Long serviceId,
            IncidentStatus status
    );

    long countByStatus(IncidentStatus status);
}
