package com.hasan.apiwatch.entity;

import com.hasan.apiwatch.enums.HealthStatus;
import com.hasan.apiwatch.enums.FailureType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "health_checks")
public class HealthCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private MonitoredService monitoredService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HealthStatus status;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 30)
    private FailureType failureType;

    @Column(name = "retry_after_seconds")
    private Long retryAfterSeconds;

    @Column(name = "rate_limit_remaining")
    private Long rateLimitRemaining;

    @Column(name = "rate_limit_reset_at")
    private Instant rateLimitResetAt;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @PrePersist
    void onCreate() {
        if (checkedAt == null) {
            checkedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public MonitoredService getMonitoredService() {
        return monitoredService;
    }

    public void setMonitoredService(MonitoredService monitoredService) {
        this.monitoredService = monitoredService;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public void setStatus(HealthStatus status) {
        this.status = status;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public void setFailureType(FailureType failureType) {
        this.failureType = failureType;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(Long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRateLimitRemaining() {
        return rateLimitRemaining;
    }

    public void setRateLimitRemaining(Long rateLimitRemaining) {
        this.rateLimitRemaining = rateLimitRemaining;
    }

    public Instant getRateLimitResetAt() {
        return rateLimitResetAt;
    }

    public void setRateLimitResetAt(Instant rateLimitResetAt) {
        this.rateLimitResetAt = rateLimitResetAt;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }
}
