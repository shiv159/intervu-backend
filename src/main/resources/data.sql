INSERT INTO questions (
    id,
    title,
    prompt,
    mode,
    difficulty,
    seniority,
    tags,
    expected_concepts,
    rubric,
    status,
    active,
    version
)
VALUES
(
    '10000000-0000-0000-0000-000000000001',
    'Two Sum in Java',
    'Write a Java solution for Two Sum and explain the time and space complexity.',
    'CODE',
    'EASY',
    'SENIOR',
    '["arrays", "hash-map", "complexity"]'::jsonb,
    '["hash map lookup", "pair tracking", "time complexity", "space complexity"]'::jsonb,
    '{"correctness": 35, "complexity": 20, "edgeCases": 15, "codeQuality": 15, "communication": 15}'::jsonb,
    'PUBLISHED',
    TRUE,
    1
),
(
    '10000000-0000-0000-0000-000000000002',
    'Design a URL Shortener',
    'Design a URL shortener that can handle high traffic and explain the data model, caching, and collision handling.',
    'SYSTEM_DESIGN',
    'MEDIUM',
    'SENIOR',
    '["system-design", "caching", "scalability"]'::jsonb,
    '["api design", "data model", "collision handling", "cache", "scaling", "reliability"]'::jsonb,
    '{"requirementsUnderstanding": 15, "architecture": 20, "dataModel": 15, "scalability": 15, "tradeoffs": 15, "reliability": 10, "communication": 10}'::jsonb,
    'PUBLISHED',
    TRUE,
    1
),
(
    '10000000-0000-0000-0000-000000000003',
    'Tell Me About a Production Incident',
    'Tell me about a production incident you handled, what you learned, and what you changed afterward.',
    'CONVERSATIONAL',
    'MEDIUM',
    'SENIOR',
    '["behavioral", "incident-response", "ownership"]'::jsonb,
    '["specific example", "ownership", "reflection", "outcome"]'::jsonb,
    '{"relevance": 25, "specificity": 25, "ownership": 20, "reflection": 15, "communication": 15}'::jsonb,
    'PUBLISHED',
    TRUE,
    1
)
ON CONFLICT (id) DO NOTHING;
