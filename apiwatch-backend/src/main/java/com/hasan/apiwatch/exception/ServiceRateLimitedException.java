package com.hasan.apiwatch.exception;

import java.time.Duration;
import java.time.Instant;

public class ServiceRateLimitedException extends RuntimeException {

    private final Instant retryAt;

    public ServiceRateLimitedException(Long serviceId, Instant retryAt) {
        super("Service " + serviceId + " is rate limited until " + retryAt);
        this.retryAt = retryAt;
    }

    public Instant getRetryAt() {
        return retryAt;
    }

    public long getRetryAfterSeconds() {
        return Math.max(1, Duration.between(Instant.now(), retryAt).toSeconds());
    }
}
