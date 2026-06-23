package com.hasan.apiwatch.entity;

import com.hasan.apiwatch.enums.NotificationProvider;
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
@Table(name = "notification_settings")
public class NotificationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationProvider provider = NotificationProvider.WEBHOOK;

    @Column(name = "webhook_url_encrypted", columnDefinition = "TEXT")
    private String webhookUrlEncrypted;

    @Column(name = "destination_encrypted", columnDefinition = "TEXT")
    private String destinationEncrypted;

    @Column(name = "cooldown_seconds", nullable = false)
    private int cooldownSeconds = 300;

    @Column(name = "escalation_minutes", nullable = false)
    private int escalationMinutes;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public NotificationProvider getProvider() {
        return provider;
    }

    public void setProvider(NotificationProvider provider) {
        this.provider = provider;
    }

    public String getWebhookUrlEncrypted() {
        return webhookUrlEncrypted;
    }

    public void setWebhookUrlEncrypted(String webhookUrlEncrypted) {
        this.webhookUrlEncrypted = webhookUrlEncrypted;
    }

    public String getDestinationEncrypted() {
        return destinationEncrypted;
    }

    public void setDestinationEncrypted(String destinationEncrypted) {
        this.destinationEncrypted = destinationEncrypted;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getEscalationMinutes() {
        return escalationMinutes;
    }

    public void setEscalationMinutes(int escalationMinutes) {
        this.escalationMinutes = escalationMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
