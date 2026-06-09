ALTER TABLE services
    ADD COLUMN rate_limited_until TIMESTAMP WITH TIME ZONE;

ALTER TABLE health_checks
    ADD COLUMN failure_type VARCHAR(30),
    ADD COLUMN retry_after_seconds BIGINT,
    ADD COLUMN rate_limit_remaining BIGINT,
    ADD COLUMN rate_limit_reset_at TIMESTAMP WITH TIME ZONE;
