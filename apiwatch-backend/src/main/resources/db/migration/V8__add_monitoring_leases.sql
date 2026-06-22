CREATE TABLE monitoring_leases (
    service_id BIGINT PRIMARY KEY REFERENCES services(id) ON DELETE CASCADE,
    owner_id VARCHAR(160) NOT NULL,
    leased_until TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_monitoring_leases_leased_until
    ON monitoring_leases(leased_until);
