package com.hasan.apiwatch.repository;

import com.hasan.apiwatch.entity.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long>,
        JpaSpecificationExecutor<MonitoredService> {
    List<MonitoredService> findAllByActiveTrueOrderByNameAsc();

    List<MonitoredService> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
