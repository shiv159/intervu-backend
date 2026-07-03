CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    prompt TEXT NOT NULL,
    mode TEXT NOT NULL CHECK (mode IN ('CODE', 'SYSTEM_DESIGN', 'CONVERSATIONAL')),
    difficulty TEXT NOT NULL,
    seniority TEXT NOT NULL,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_concepts JSONB NOT NULL DEFAULT '[]'::jsonb,
    rubric JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE question_embeddings (
    question_id UUID PRIMARY KEY REFERENCES questions(id),
    embedding vector(1536),
    model_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE interview_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id TEXT NOT NULL,
    target_role TEXT NOT NULL,
    state TEXT NOT NULL,
    mode TEXT NOT NULL,
    seniority TEXT NOT NULL,
    difficulty TEXT NOT NULL,
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    focus_areas JSONB NOT NULL DEFAULT '[]'::jsonb,
    current_question_id UUID REFERENCES questions(id),
    state_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE interview_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES interview_sessions(id),
    question_id UUID REFERENCES questions(id),
    idempotency_key TEXT NOT NULL,
    interaction_type TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES interview_sessions(id),
    interaction_id UUID REFERENCES interview_interactions(id),
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    rubric_scores JSONB NOT NULL DEFAULT '{}'::jsonb,
    strengths JSONB NOT NULL DEFAULT '[]'::jsonb,
    gaps JSONB NOT NULL DEFAULT '[]'::jsonb,
    follow_up_question TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evaluation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_id UUID REFERENCES evaluations(id),
    provider TEXT NOT NULL,
    evaluator_version TEXT NOT NULL,
    schema_valid BOOLEAN NOT NULL DEFAULT TRUE,
    raw_response JSONB NOT NULL DEFAULT '{}'::jsonb,
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE session_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES interview_sessions(id),
    event_version INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE analytics_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id TEXT NOT NULL,
    topic TEXT NOT NULL,
    snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_questions_status ON questions(status);
CREATE INDEX idx_questions_tags ON questions USING GIN(tags);
CREATE INDEX idx_interview_sessions_owner_id ON interview_sessions(owner_id);
CREATE UNIQUE INDEX idx_session_events_session_id_version ON session_events(session_id, event_version);
CREATE UNIQUE INDEX idx_interview_interactions_idempotency_key ON interview_interactions(session_id, idempotency_key);
CREATE INDEX idx_evaluations_session_id ON evaluations(session_id);

INSERT INTO questions (title, prompt, mode, difficulty, seniority, tags, expected_concepts, rubric, status)
VALUES
('Two Sum', 'Given an array of integers and a target, return the indexes of two numbers that add up to the target.', 'CODE', 'EASY', 'MID', '["arrays","hash-map"]', '["hash map lookup","edge cases","complexity"]', '{"correctness":40,"complexity":25,"edgeCases":20,"communication":15}', 'PUBLISHED'),
('Design a URL Shortener', 'Design a URL shortening service that supports creating and resolving short links at high read volume.', 'SYSTEM_DESIGN', 'MEDIUM', 'SENIOR', '["system-design","scalability","data-model"]', '["api design","storage","collision handling","caching"]', '{"architecture":30,"dataModel":25,"scalability":25,"tradeoffs":20}', 'PUBLISHED'),
('Handling Production Incidents', 'Tell me about a time you handled a production incident and what you changed afterward.', 'CONVERSATIONAL', 'MEDIUM', 'MID', '["behavioral","ownership","incident-response"]', '["specific situation","ownership","reflection"]', '{"specificity":30,"ownership":30,"reflection":25,"communication":15}', 'PUBLISHED');
