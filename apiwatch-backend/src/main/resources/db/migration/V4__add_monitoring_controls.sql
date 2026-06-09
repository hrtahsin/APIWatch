ALTER TABLE services
    ADD COLUMN expected_status_min INT NOT NULL DEFAULT 200,
    ADD COLUMN expected_status_max INT NOT NULL DEFAULT 299,
    ADD COLUMN check_interval_seconds INT NOT NULL DEFAULT 60,
    ADD COLUMN response_body_contains VARCHAR(500);

UPDATE services
SET expected_status_min = expected_status_code,
    expected_status_max = expected_status_code;
