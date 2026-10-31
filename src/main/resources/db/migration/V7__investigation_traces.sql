-- Flyway migration V7: Investigation execution traces for agentic observability
-- Tracks multi-agent stage execution, tool invocations, latencies, tokens, and evidence references

CREATE TABLE investigation_traces (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID,
    incident_id         UUID            NOT NULL,
    stage               VARCHAR(50)     NOT NULL,
    agent_name          VARCHAR(50),
    tool_name           VARCHAR(100),
    tool_arguments      TEXT,
    summary             TEXT,
    output_data         TEXT,
    status              VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    duration_ms         BIGINT,
    llm_latency_ms      BIGINT,
    tokens_used         INTEGER,
    error_message       TEXT,
    evidence_references TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_traces_incident
        FOREIGN KEY (incident_id)
        REFERENCES incidents (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_traces_job
        FOREIGN KEY (job_id)
        REFERENCES investigation_jobs (id)
        ON DELETE SET NULL
);

CREATE INDEX idx_investigation_traces_incident ON investigation_traces (incident_id);
CREATE INDEX idx_investigation_traces_job ON investigation_traces (job_id);
CREATE INDEX idx_investigation_traces_created ON investigation_traces (created_at ASC);
