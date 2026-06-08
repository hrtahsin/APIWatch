package com.hasan.apiwatch.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/mock")
public class MockServiceController {

    @GetMapping("/healthy")
    Map<String, Object> healthy() {
        return Map.of("status", "ok", "timestamp", Instant.now().toString());
    }

    @GetMapping("/slow")
    Map<String, Object> slow() throws InterruptedException {
        Thread.sleep(2500);
        return Map.of("status", "ok", "latency", "simulated");
    }

    @GetMapping("/failing")
    ResponseEntity<Map<String, String>> failing() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "unavailable"));
    }
}
