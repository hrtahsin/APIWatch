package com.hasan.apiwatch.controller;

import com.hasan.apiwatch.dto.CreateServiceRequest;
import com.hasan.apiwatch.dto.ServiceResponse;
import com.hasan.apiwatch.dto.UpdateServiceRequest;
import com.hasan.apiwatch.service.ServiceMonitorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class MonitoredServiceController {

    private final ServiceMonitorService serviceMonitorService;

    public MonitoredServiceController(ServiceMonitorService serviceMonitorService) {
        this.serviceMonitorService = serviceMonitorService;
    }

    @PostMapping
    ResponseEntity<ServiceResponse> create(@Valid @RequestBody CreateServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceMonitorService.create(request));
    }

    @GetMapping
    List<ServiceResponse> findAll() {
        return serviceMonitorService.findAll();
    }

    @GetMapping("/{id}")
    ServiceResponse findById(@PathVariable Long id) {
        return serviceMonitorService.findById(id);
    }

    @PutMapping("/{id}")
    ServiceResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateServiceRequest request
    ) {
        return serviceMonitorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        serviceMonitorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
