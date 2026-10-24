-- API Sentinel — Baseline Database Migration (V1)
-- Enforce extensions and baseline system verification table

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS system_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(100) NOT NULL UNIQUE,
    value TEXT NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial system metadata
INSERT INTO system_metadata (key, value, description)
VALUES 
    ('SCHEMA_VERSION', '1.0.0', 'API Sentinel database baseline schema version'),
    ('PLATFORM_STATUS', 'OPERATIONAL', 'Initial system health status flag'),
    ('INITIALIZED_AT', to_char(CURRENT_TIMESTAMP AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'), 'System initialization timestamp in UTC')
ON CONFLICT (key) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_system_metadata_key ON system_metadata(key);
