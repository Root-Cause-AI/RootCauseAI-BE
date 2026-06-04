-- Flyway migration V3: Add Phase 2 fields to reports table

ALTER TABLE reports
    ADD COLUMN severity VARCHAR(20),
    ADD COLUMN tags TEXT,
    ADD COLUMN executive_summary TEXT,
    ADD COLUMN engineering_summary TEXT,
    ADD COLUMN timeline TEXT,
    ADD COLUMN similar_incidents TEXT;

-- Update existing records if necessary (optional, just leaves them null for now, 
-- but if we want to query them we should probably default severity to something. 
-- For now we allow nulls since older reports might not have them, or we can update them).
-- Wait, if they are null, filtering by severity might just omit them, which is fine.

CREATE INDEX idx_reports_severity ON reports (severity);
