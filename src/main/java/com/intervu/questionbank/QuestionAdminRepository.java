package com.intervu.questionbank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QuestionAdminRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Integer>> RUBRIC_MAP = new TypeReference<>() {
    };

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

    public Optional<QuestionDef> findQuestionDefById(UUID id) {
        return jdbcTemplate.query(
            """
            SELECT title, prompt, mode, difficulty, seniority, tags, expected_concepts, rubric
            FROM questions
            WHERE id = ?
            """,
            this::mapQuestionDef,
            id
        ).stream().findFirst();
    }

    public Optional<QuestionDef> findPublishedQuestionDefById(UUID id) {
        return jdbcTemplate.query(
            """
            SELECT title, prompt, mode, difficulty, seniority, tags, expected_concepts, rubric
            FROM questions
            WHERE id = ? AND status = 'PUBLISHED'
            """,
            this::mapQuestionDef,
            id
        ).stream().findFirst();
    }

    public int publishQuestion(UUID id) {
        return jdbcTemplate.update(
            """
            UPDATE questions
            SET status = 'PUBLISHED', active = FALSE
            WHERE id = ? AND status IN ('DRAFT', 'REVIEWED')
            """,
            id
        );
    }

    public int activateQuestion(UUID id) {
        return jdbcTemplate.update(
            """
            UPDATE questions
            SET active = TRUE
            WHERE id = ? AND status = 'PUBLISHED'
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

    private QuestionDef mapQuestionDef(ResultSet rs, int rowNum) throws SQLException {
        return new QuestionDef(
            rs.getString("title"),
            rs.getString("prompt"),
            rs.getString("mode"),
            rs.getString("difficulty"),
            rs.getString("seniority"),
            readStringList(rs.getString("tags")),
            readStringList(rs.getString("expected_concepts")),
            readRubric(rs.getString("rubric"))
        );
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse question list JSON", e);
        }
    }

    private Map<String, Integer> readRubric(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, RUBRIC_MAP);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse question rubric JSON", e);
        }
    }
}
