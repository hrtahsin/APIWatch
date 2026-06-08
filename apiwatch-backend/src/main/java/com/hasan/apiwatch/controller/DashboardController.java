package com.hasan.apiwatch.controller;

import com.hasan.apiwatch.dto.DashboardSummaryResponse;
import com.hasan.apiwatch.dto.ServiceMetricsResponse;
import com.hasan.apiwatch.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final MetricsService metricsService;

    public DashboardController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/dashboard/summary")
    DashboardSummaryResponse dashboardSummary() {
        return metricsService.dashboardSummary();
    }

    @GetMapping("/services/{serviceId}/metrics")
    ServiceMetricsResponse serviceMetrics(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "24") int windowHours
    ) {
        return metricsService.serviceMetrics(serviceId, windowHours);
    }
}
