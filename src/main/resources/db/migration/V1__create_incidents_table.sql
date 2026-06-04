-- Flyway migration V1: Create incidents table
-- Stores production incident records with associated logs and deployment information.

CREATE TABLE incidents (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255)    NOT NULL,
    logs            TEXT,
    deployment_info TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_incidents_created_at ON incidents (created_at DESC);
