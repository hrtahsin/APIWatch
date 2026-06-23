package com.hasan.apiwatch.repository;

import com.hasan.apiwatch.entity.MonitoringLease;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MonitoringLeaseRepository extends JpaRepository<MonitoringLease, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lease from MonitoringLease lease where lease.serviceId = :serviceId")
    Optional<MonitoringLease> findByServiceIdForUpdate(@Param("serviceId") Long serviceId);
}
