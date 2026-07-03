-- Move metadata columns to evaluations where they are actually queried

ALTER TABLE evaluations
ADD COLUMN IF NOT EXISTS model VARCHAR(255),
ADD COLUMN IF NOT EXISTS provider VARCHAR(255),
ADD COLUMN IF NOT EXISTS latency_ms BIGINT,
ADD COLUMN IF NOT EXISTS cost DECIMAL(10, 5);

ALTER TABLE evaluation_runs
DROP COLUMN IF EXISTS model,
DROP COLUMN IF EXISTS provider,
DROP COLUMN IF EXISTS latency_ms,
DROP COLUMN IF EXISTS cost;
