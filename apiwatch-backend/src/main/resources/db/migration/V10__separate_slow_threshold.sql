ALTER TABLE services
    ADD COLUMN slow_threshold_ms INT;

UPDATE services
SET slow_threshold_ms = timeout_ms;

ALTER TABLE services
    ALTER COLUMN slow_threshold_ms SET NOT NULL,
    ALTER COLUMN slow_threshold_ms SET DEFAULT 2000;
