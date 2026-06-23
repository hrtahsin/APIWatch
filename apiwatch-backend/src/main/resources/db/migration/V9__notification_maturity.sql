ALTER TABLE notification_settings
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'WEBHOOK',
    ADD COLUMN destination_encrypted TEXT,
    ADD COLUMN escalation_minutes INT NOT NULL DEFAULT 0;

UPDATE notification_settings
SET destination_encrypted = webhook_url_encrypted
WHERE destination_encrypted IS NULL
  AND webhook_url_encrypted IS NOT NULL;

ALTER TABLE services
    ADD COLUMN notify_on_incident_open BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notify_on_incident_resolve BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notification_escalation_minutes INT NOT NULL DEFAULT 0;

ALTER TABLE notification_deliveries
    ADD COLUMN provider VARCHAR(30) NOT NULL DEFAULT 'WEBHOOK',
    ADD COLUMN destination_display VARCHAR(255),
    ADD COLUMN destination_encrypted TEXT,
    ADD COLUMN payload_json TEXT,
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_notification_delivery_outbox
    ON notification_deliveries(status, next_attempt_at ASC);
