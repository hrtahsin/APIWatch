package com.hasan.apiwatch.controller;

import com.hasan.apiwatch.dto.CreateServiceRequest;
import com.hasan.apiwatch.dto.PageResponse;
import com.hasan.apiwatch.dto.ServiceResponse;
import com.hasan.apiwatch.dto.UpdateServiceActiveRequest;
import com.hasan.apiwatch.dto.UpdateServiceRequest;
import com.hasan.apiwatch.service.ServiceMonitorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    PageResponse<ServiceResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return serviceMonitorService.findAll(page, size);
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

    @PatchMapping("/{id}/active")
    ServiceResponse setActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateServiceActiveRequest request
    ) {
        return serviceMonitorService.setActive(id, request.active());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        serviceMonitorService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
