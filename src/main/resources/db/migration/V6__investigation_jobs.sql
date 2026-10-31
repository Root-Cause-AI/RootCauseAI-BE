-- Flyway migration V6: Investigation jobs table for async agentic investigation
-- Tracks the lifecycle of asynchronous AI investigations: PENDING → IN_PROGRESS → COMPLETED | FAILED

CREATE TABLE investigation_jobs (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id     UUID            NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    stage           VARCHAR(100),
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    error_message   TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_jobs_incident
        FOREIGN KEY (incident_id)
        REFERENCES incidents (id)
        ON DELETE CASCADE,

    CONSTRAINT chk_jobs_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_investigation_jobs_incident ON investigation_jobs (incident_id);
CREATE INDEX idx_investigation_jobs_status ON investigation_jobs (status);
CREATE INDEX idx_investigation_jobs_created ON investigation_jobs (created_at DESC);
