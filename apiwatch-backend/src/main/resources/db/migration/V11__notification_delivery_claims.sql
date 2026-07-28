ALTER TABLE notification_deliveries
    ADD COLUMN event_key VARCHAR(200),
    ADD COLUMN claimed_by VARCHAR(160),
    ADD COLUMN claimed_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_attempt_at TIMESTAMP WITH TIME ZONE;

UPDATE notification_deliveries
SET event_key = 'legacy:' || id;

ALTER TABLE notification_deliveries
    ALTER COLUMN event_key SET NOT NULL;

CREATE UNIQUE INDEX uq_notification_delivery_event_key
    ON notification_deliveries(event_key);

DROP INDEX idx_notification_delivery_outbox;

CREATE INDEX idx_notification_delivery_outbox
    ON notification_deliveries(status, next_attempt_at, claimed_until);
