package com.hasan.apiwatch.repository;

import com.hasan.apiwatch.entity.MonitoringLease;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MonitoringLeaseRepository extends JpaRepository<MonitoringLease, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO monitoring_leases (
                service_id,
                owner_id,
                leased_until,
                created_at,
                updated_at
            )
            VALUES (:serviceId, :ownerId, :leasedUntil, :now, :now)
            ON CONFLICT (service_id) DO UPDATE
            SET owner_id = EXCLUDED.owner_id,
                leased_until = EXCLUDED.leased_until,
                updated_at = EXCLUDED.updated_at
            WHERE monitoring_leases.leased_until <= :now
               OR monitoring_leases.owner_id = :ownerId
            """, nativeQuery = true)
    int tryAcquire(
            @Param("serviceId") Long serviceId,
            @Param("ownerId") String ownerId,
            @Param("now") Instant now,
            @Param("leasedUntil") Instant leasedUntil
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lease from MonitoringLease lease where lease.serviceId = :serviceId")
    Optional<MonitoringLease> findByServiceIdForUpdate(@Param("serviceId") Long serviceId);
}
