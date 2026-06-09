CREATE TABLE notification_settings (
    id BIGSERIAL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    webhook_url_encrypted TEXT,
    cooldown_seconds INT NOT NULL DEFAULT 300,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_deliveries (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    service_id BIGINT NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    http_status_code INT,
    error_message VARCHAR(500),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_delivery_cooldown
    ON notification_deliveries(service_id, event_type, status, attempted_at DESC);
