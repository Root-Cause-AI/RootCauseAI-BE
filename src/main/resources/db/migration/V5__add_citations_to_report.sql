-- Add citations column to investigation_reports table to persist AI extracted citations
ALTER TABLE reports
ADD COLUMN citations JSONB;
