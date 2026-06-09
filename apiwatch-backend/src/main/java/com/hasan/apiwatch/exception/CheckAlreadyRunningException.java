package com.hasan.apiwatch.exception;

public class CheckAlreadyRunningException extends RuntimeException {
    public CheckAlreadyRunningException(Long serviceId) {
        super("A health check is already running for service " + serviceId);
    }
}
