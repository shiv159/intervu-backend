CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS questions (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    prompt TEXT NOT NULL,
    mode TEXT NOT NULL CHECK (mode IN ('CODE', 'SYSTEM_DESIGN', 'CONVERSATIONAL')),
    difficulty TEXT NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    seniority TEXT NOT NULL CHECK (seniority IN ('JUNIOR', 'MID', 'SENIOR', 'STAFF')),
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_concepts JSONB NOT NULL DEFAULT '[]'::jsonb,
    rubric JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'REVIEWED', 'PUBLISHED', 'ARCHIVED')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS question_embeddings (
    question_id UUID PRIMARY KEY REFERENCES questions(id) ON DELETE CASCADE,
    embedding vector(1536) NOT NULL,
    model VARCHAR(100) NOT NULL,
    embedded_text_hash VARCHAR(100),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS interview_sessions (
    id UUID PRIMARY KEY,
    owner_id TEXT NOT NULL,
    target_role TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN (
        'CREATED',
        'IN_PROGRESS',
        'WAITING_EVALUATION',
        'EVALUATED',
        'EVALUATION_FAILED',
        'COMPLETED',
        'EXPIRED',
        'ABANDONED'
    )),
    mode TEXT NOT NULL CHECK (mode IN ('CODE', 'SYSTEM_DESIGN', 'CONVERSATIONAL')),
    seniority TEXT NOT NULL CHECK (seniority IN ('JUNIOR', 'MID', 'SENIOR', 'STAFF')),
    difficulty TEXT NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    focus_areas JSONB NOT NULL DEFAULT '[]'::jsonb,
    current_question_id UUID REFERENCES questions(id),
    current_question_version INT,
    adaptive_state JSONB NOT NULL DEFAULT '{}'::jsonb,
    state_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS interview_interactions (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    question_id UUID REFERENCES questions(id),
    question_version INT,
    idempotency_key TEXT NOT NULL,
    interaction_type TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS evaluations (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    interaction_id UUID NOT NULL REFERENCES interview_interactions(id) ON DELETE CASCADE,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    rubric_scores JSONB NOT NULL DEFAULT '{}'::jsonb,
    strengths JSONB NOT NULL DEFAULT '[]'::jsonb,
    gaps JSONB NOT NULL DEFAULT '[]'::jsonb,
    follow_up_question TEXT,
    model VARCHAR(255),
    provider VARCHAR(255),
    latency_ms BIGINT,
    cost NUMERIC(12, 6),
    evaluator_version VARCHAR(100),
    prompt_version VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (interaction_id)
);

CREATE TABLE IF NOT EXISTS session_events (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    event_version BIGINT NOT NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, event_version)
);

CREATE INDEX IF NOT EXISTS idx_questions_status ON questions (status);
CREATE INDEX IF NOT EXISTS idx_questions_tags ON questions USING GIN (tags);
CREATE INDEX IF NOT EXISTS idx_questions_mode_seniority ON questions (mode, seniority);
CREATE INDEX IF NOT EXISTS idx_interview_sessions_owner_id ON interview_sessions (owner_id);
CREATE INDEX IF NOT EXISTS idx_interview_sessions_state ON interview_sessions (state);
CREATE UNIQUE INDEX IF NOT EXISTS idx_interview_interactions_idempotency_key
    ON interview_interactions (session_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_interview_interactions_session_id ON interview_interactions (session_id);
CREATE INDEX IF NOT EXISTS idx_evaluations_session_id ON evaluations (session_id);
CREATE INDEX IF NOT EXISTS idx_session_events_session_id_version ON session_events (session_id, event_version);

CREATE TABLE IF NOT EXISTS analytics_snapshots (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_analytics_snapshots_session_id ON analytics_snapshots (session_id);

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

CREATE TABLE IF NOT EXISTS resume_extracts (
    id UUID PRIMARY KEY,
    owner_id TEXT NOT NULL,
    source_filename TEXT,
    extracted_text TEXT,
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    focus_areas JSONB NOT NULL DEFAULT '[]'::jsonb,
    claims JSONB NOT NULL DEFAULT '[]'::jsonb,
    target_role TEXT,
    seniority TEXT,
    parser_version VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS jd_extracts (
    id UUID PRIMARY KEY,
    owner_id TEXT NOT NULL,
    source_text TEXT,
    requirements JSONB NOT NULL DEFAULT '[]'::jsonb,
    technologies JSONB NOT NULL DEFAULT '[]'::jsonb,
    responsibilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    seniority TEXT,
    extractor_version VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_resume_extracts_owner_id ON resume_extracts (owner_id);
CREATE INDEX IF NOT EXISTS idx_jd_extracts_owner_id ON jd_extracts (owner_id);

-- ------------------------------------------------------------------
-- Idempotent migrations for databases created before these columns existed
-- ------------------------------------------------------------------
ALTER TABLE interview_sessions
    ADD COLUMN IF NOT EXISTS current_question_version INT;

ALTER TABLE interview_interactions
    ADD COLUMN IF NOT EXISTS question_version INT;

ALTER TABLE evaluations
    ADD COLUMN IF NOT EXISTS evaluator_version VARCHAR(100);

ALTER TABLE evaluations
    ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(100);

ALTER TABLE interview_sessions
    ADD COLUMN IF NOT EXISTS adaptive_state JSONB;

UPDATE interview_sessions SET adaptive_state = '{}'::jsonb WHERE adaptive_state IS NULL;
ALTER TABLE interview_sessions ALTER COLUMN adaptive_state SET DEFAULT '{}'::jsonb;
ALTER TABLE interview_sessions ALTER COLUMN adaptive_state SET NOT NULL;
