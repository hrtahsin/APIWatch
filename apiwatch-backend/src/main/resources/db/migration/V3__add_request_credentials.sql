ALTER TABLE services
    ADD COLUMN custom_headers_encrypted TEXT,
    ADD COLUMN auth_type VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN auth_header_name VARCHAR(120),
    ADD COLUMN auth_value_encrypted TEXT;
