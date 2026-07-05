package com.intervu.questionbank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Repository
public class QuestionAdminRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public QuestionAdminRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean exists(String title, String mode, String seniority) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM questions WHERE title = ? AND mode = ? AND seniority = ?",
            Integer.class,
            title, mode, seniority
        );
        return count != null && count > 0;
    }

    public void insertDraftQuestion(UUID id, QuestionDef def) {
        try {
            String tagsJson = objectMapper.writeValueAsString(def.tags());
            String expectedConceptsJson = objectMapper.writeValueAsString(def.expectedConcepts());
            String rubricJson = objectMapper.writeValueAsString(def.rubric());

            jdbcTemplate.update(
                """
                INSERT INTO questions (
                    id, title, prompt, mode, difficulty, seniority,
                    tags, expected_concepts, rubric,
                    status, active, version, created_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?,
                    ?::jsonb, ?::jsonb, ?::jsonb,
                    'DRAFT', FALSE, 1, now()
                )
                """,
                id,
                def.title(),
                def.prompt(),
                def.mode(),
                def.difficulty(),
                def.seniority(),
                tagsJson,
                expectedConceptsJson,
                rubricJson
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON fields for question: " + def.title(), e);
        }
    }

    public int publishQuestion(UUID id) {
        return jdbcTemplate.update(
            """
            UPDATE questions
            SET status = 'PUBLISHED', active = TRUE
            WHERE id = ? AND status IN ('DRAFT', 'REVIEWED')
            """,
            id
        );
    }

    public int archiveQuestion(UUID id) {
        return jdbcTemplate.update(
            """
            UPDATE questions
            SET status = 'ARCHIVED', active = FALSE
            WHERE id = ?
            """,
            id
        );
    }
}
