-- Migration to add metadata columns to evaluation_runs

ALTER TABLE evaluation_runs
ADD COLUMN IF NOT EXISTS model VARCHAR(255),
ADD COLUMN IF NOT EXISTS provider VARCHAR(255),
ADD COLUMN IF NOT EXISTS latency_ms BIGINT,
ADD COLUMN IF NOT EXISTS cost DECIMAL(10, 5);
