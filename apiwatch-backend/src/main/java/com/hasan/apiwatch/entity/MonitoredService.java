package com.hasan.apiwatch.entity;

import com.hasan.apiwatch.enums.HttpMethodType;
import com.hasan.apiwatch.enums.RequestAuthType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "services")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "owner_name", length = 120)
    private String ownerName;

    @Column(name = "team_name", length = 120)
    private String teamName;

    @Column(length = 500)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HttpMethodType method = HttpMethodType.GET;

    @Column(name = "expected_status_code", nullable = false)
    private int expectedStatusCode = 200;

    @Column(name = "expected_status_min", nullable = false)
    private int expectedStatusMin = 200;

    @Column(name = "expected_status_max", nullable = false)
    private int expectedStatusMax = 299;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 2000;

    @Column(name = "check_interval_seconds", nullable = false)
    private int checkIntervalSeconds = 60;

    @Column(name = "response_body_contains", length = 500)
    private String responseBodyContains;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold = 3;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "rate_limited_until")
    private Instant rateLimitedUntil;

    @Column(name = "custom_headers_encrypted", columnDefinition = "TEXT")
    private String customHeadersEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private RequestAuthType authType = RequestAuthType.NONE;

    @Column(name = "auth_header_name", length = 120)
    private String authHeaderName;

    @Column(name = "auth_value_encrypted", columnDefinition = "TEXT")
    private String authValueEncrypted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public HttpMethodType getMethod() {
        return method;
    }

    public void setMethod(HttpMethodType method) {
        this.method = method;
    }

    public int getExpectedStatusCode() {
        return expectedStatusCode;
    }

    public void setExpectedStatusCode(int expectedStatusCode) {
        this.expectedStatusCode = expectedStatusCode;
    }

    public int getExpectedStatusMin() {
        return expectedStatusMin;
    }

    public void setExpectedStatusMin(int expectedStatusMin) {
        this.expectedStatusMin = expectedStatusMin;
    }

    public int getExpectedStatusMax() {
        return expectedStatusMax;
    }

    public void setExpectedStatusMax(int expectedStatusMax) {
        this.expectedStatusMax = expectedStatusMax;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public void setCheckIntervalSeconds(int checkIntervalSeconds) {
        this.checkIntervalSeconds = checkIntervalSeconds;
    }

    public String getResponseBodyContains() {
        return responseBodyContains;
    }

    public void setResponseBodyContains(String responseBodyContains) {
        this.responseBodyContains = responseBodyContains;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getRateLimitedUntil() {
        return rateLimitedUntil;
    }

    public void setRateLimitedUntil(Instant rateLimitedUntil) {
        this.rateLimitedUntil = rateLimitedUntil;
    }

    public String getCustomHeadersEncrypted() {
        return customHeadersEncrypted;
    }

    public void setCustomHeadersEncrypted(String customHeadersEncrypted) {
        this.customHeadersEncrypted = customHeadersEncrypted;
    }

    public RequestAuthType getAuthType() {
        return authType;
    }

    public void setAuthType(RequestAuthType authType) {
        this.authType = authType;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    public String getAuthValueEncrypted() {
        return authValueEncrypted;
    }

    public void setAuthValueEncrypted(String authValueEncrypted) {
        this.authValueEncrypted = authValueEncrypted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
