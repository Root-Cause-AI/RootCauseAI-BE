-- Flyway migration V2: Create reports table
-- Stores AI-generated investigation reports linked to incidents.

CREATE TABLE reports (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id         UUID            NOT NULL UNIQUE,
    root_cause          TEXT            NOT NULL,
    confidence_score    INTEGER         NOT NULL CHECK (confidence_score BETWEEN 0 AND 100),
    evidence            TEXT            NOT NULL,
    recommendations     TEXT            NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_reports_incident
        FOREIGN KEY (incident_id)
        REFERENCES incidents (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_reports_incident_id ON reports (incident_id);
