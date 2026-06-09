package com.hasan.apiwatch.controller;

import com.hasan.apiwatch.dto.HealthCheckResponse;
import com.hasan.apiwatch.dto.PageResponse;
import com.hasan.apiwatch.service.HealthCheckQueryService;
import com.hasan.apiwatch.service.HealthCheckRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services/{serviceId}")
public class HealthCheckController {

    private final HealthCheckRunner healthCheckRunner;
    private final HealthCheckQueryService healthCheckQueryService;

    public HealthCheckController(
            HealthCheckRunner healthCheckRunner,
            HealthCheckQueryService healthCheckQueryService
    ) {
        this.healthCheckRunner = healthCheckRunner;
        this.healthCheckQueryService = healthCheckQueryService;
    }

    @PostMapping("/check")
    HealthCheckResponse runCheck(@PathVariable Long serviceId) {
        return healthCheckRunner.run(serviceId);
    }

    @GetMapping("/health-checks")
    PageResponse<HealthCheckResponse> findRecent(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return healthCheckQueryService.findRecent(serviceId, page, size);
    }
}
