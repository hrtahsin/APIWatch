package com.hasan.apiwatch.controller;

import com.hasan.apiwatch.dto.IncidentResponse;
import com.hasan.apiwatch.dto.PageResponse;
import com.hasan.apiwatch.enums.IncidentStatus;
import com.hasan.apiwatch.service.IncidentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    PageResponse<IncidentResponse> findAll(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return incidentService.findAll(status, serviceId, page, size);
    }

    @GetMapping("/{id}")
    IncidentResponse findById(@PathVariable Long id) {
        return incidentService.findById(id);
    }

    @PatchMapping("/{id}/resolve")
    IncidentResponse resolve(@PathVariable Long id) {
        return incidentService.resolve(id);
    }
}
