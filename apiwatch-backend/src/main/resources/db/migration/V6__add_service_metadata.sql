ALTER TABLE services
    ADD COLUMN owner_name VARCHAR(120),
    ADD COLUMN team_name VARCHAR(120),
    ADD COLUMN tags VARCHAR(500);

CREATE INDEX idx_services_active_name
    ON services(active, name);

CREATE INDEX idx_services_team_name
    ON services(team_name);
