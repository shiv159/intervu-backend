package com.intervu.interview;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.intervu.interview.InterviewDtos.QuestionPayload;

@Repository
public class QuestionRepository {

	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};
	private static final TypeReference<Map<String, Integer>> RUBRIC_MAP = new TypeReference<>() {
	};

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public QuestionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public Optional<QuestionPayload> findFirstPublishedQuestion(String mode, String seniority) {
		String normalizedMode = normalizeMode(mode);
		String normalizedSeniority = normalizeSeniority(seniority);
		List<QuestionPayload> questions = jdbcTemplate.query(
			"""
				SELECT id, title, prompt, mode, difficulty, seniority, tags, expected_concepts, rubric, version
				FROM questions
				WHERE active = TRUE
				  AND status = 'PUBLISHED'
				  AND mode = ?
				  AND seniority = ?
				ORDER BY CASE difficulty
					WHEN 'EASY' THEN 1
					WHEN 'MEDIUM' THEN 2
					WHEN 'HARD' THEN 3
					ELSE 99
				END,
				version DESC,
				created_at ASC
				LIMIT 1
				""",
			this::mapQuestion,
			normalizedMode,
			normalizedSeniority
		);
		if (!questions.isEmpty()) {
			return Optional.of(questions.getFirst());
		}

		questions = jdbcTemplate.query(
			"""
				SELECT id, title, prompt, mode, difficulty, seniority, tags, expected_concepts, rubric, version
				FROM questions
				WHERE active = TRUE
				  AND status = 'PUBLISHED'
				  AND mode = ?
				ORDER BY CASE difficulty
					WHEN 'EASY' THEN 1
					WHEN 'MEDIUM' THEN 2
					WHEN 'HARD' THEN 3
					ELSE 99
				END,
				version DESC,
				created_at ASC
				LIMIT 1
				""",
			this::mapQuestion,
			normalizedMode
		);
		return questions.stream().findFirst();
	}

	public Optional<QuestionPayload> findNextPublishedQuestion(String mode, String seniority, List<UUID> excludedIds) {
		String normalizedMode = normalizeMode(mode);
		String normalizedSeniority = normalizeSeniority(seniority);
		
		String excludeClause = "";
		Object[] params1;
		Object[] params2;
		
		if (excludedIds != null && !excludedIds.isEmpty()) {
			String placeholders = String.join(",", Collections.nCopies(excludedIds.size(), "?"));
			excludeClause = " AND id NOT IN (" + placeholders + ") ";
			
			params1 = new Object[2 + excludedIds.size()];
			params1[0] = normalizedMode;
			params1[1] = normalizedSeniority;
			for (int i = 0; i < excludedIds.size(); i++) {
				params1[2 + i] = excludedIds.get(i);
			}
			
			params2 = new Object[1 + excludedIds.size()];
			params2[0] = normalizedMode;
			for (int i = 0; i < excludedIds.size(); i++) {
				params2[1 + i] = excludedIds.get(i);
			}
		} else {
			params1 = new Object[]{normalizedMode, normalizedSeniority};
			params2 = new Object[]{normalizedMode};
		}

		List<QuestionPayload> questions = jdbcTemplate.query(
			"""
				SELECT id, title, prompt, mode, difficulty, seniority, tags, expected_concepts, rubric, version
				FROM questions
				WHERE active = TRUE
				  AND status = 'PUBLISHED'
				  AND mode = ?
				  AND seniority = ?
				""" + excludeClause + """
				ORDER BY CASE difficulty
					WHEN 'EASY' THEN 1
					WHEN 'MEDIUM' THEN 2
					WHEN 'HARD' THEN 3
					ELSE 99
				END,
				version DESC,
				created_at ASC
				LIMIT 1
				""",
			this::mapQuestion,
			params1
		);
		if (!questions.isEmpty()) {
			return Optional.of(questions.getFirst());
		}

		questions = jdbcTemplate.query(
			"""
				SELECT id, title, prompt, mode, difficulty, seniority, tags, expected_concepts, rubric, version
				FROM questions
				WHERE active = TRUE
				  AND status = 'PUBLISHED'
				  AND mode = ?
				""" + excludeClause + """
				ORDER BY CASE difficulty
					WHEN 'EASY' THEN 1
					WHEN 'MEDIUM' THEN 2
					WHEN 'HARD' THEN 3
					ELSE 99
				END,
				version DESC,
				created_at ASC
				LIMIT 1
				""",
			this::mapQuestion,
			params2
		);
		return questions.stream().findFirst();
	}

	public Optional<QuestionPayload> findById(UUID id) {
		List<QuestionPayload> questions = jdbcTemplate.query(
			"""
				SELECT id, title, prompt, mode, difficulty, seniority, tags, expected_concepts, rubric, version
				FROM questions
				WHERE id = ?
				""",
			this::mapQuestion,
			id
		);
		return questions.stream().findFirst();
	}

	public List<QuestionRetrievalCandidate> findRetrievalCandidates(String mode, String seniority, List<UUID> excludedIds, String queryEmbeddingLiteral) {
		String normalizedMode = normalizeMode(mode);
		String normalizedSeniority = normalizeSeniority(seniority);

		String excludeClause = "";
		Object[] params;

		if (excludedIds != null && !excludedIds.isEmpty()) {
			String placeholders = String.join(",", Collections.nCopies(excludedIds.size(), "?"));
			excludeClause = " AND q.id NOT IN (" + placeholders + ") ";
			params = new Object[3 + excludedIds.size()];
			params[0] = queryEmbeddingLiteral;
			params[1] = normalizedMode;
			params[2] = normalizedSeniority;
			for (int i = 0; i < excludedIds.size(); i++) {
				params[3 + i] = excludedIds.get(i);
			}
		} else {
			params = new Object[]{queryEmbeddingLiteral, normalizedMode, normalizedSeniority};
		}

		return jdbcTemplate.query(
			"""
				SELECT q.id, q.title, q.prompt, q.mode, q.difficulty, q.seniority, q.tags, q.expected_concepts, q.rubric, q.version,
				       qe.embedded_text_hash,
				       qe.embedding <=> ?::vector AS distance
				FROM questions q
				JOIN question_embeddings qe ON qe.question_id = q.id
				WHERE q.active = TRUE
				  AND q.status = 'PUBLISHED'
				  AND q.mode = ?
				  AND q.seniority = ?
				""" + excludeClause + """
				ORDER BY distance ASC, CASE q.difficulty
					WHEN 'EASY' THEN 1
					WHEN 'MEDIUM' THEN 2
					WHEN 'HARD' THEN 3
					ELSE 99
				END,
				q.version DESC,
				q.created_at ASC
				LIMIT 50
				""",
			this::mapRetrievalCandidate,
			params
		);
	}

	private QuestionPayload mapQuestion(ResultSet rs, int rowNum) throws SQLException {
		return new QuestionPayload(
			rs.getObject("id", UUID.class),
			rs.getString("title"),
			rs.getString("prompt"),
			rs.getString("mode"),
			rs.getString("difficulty"),
			rs.getString("seniority"),
			readStringList(rs.getString("tags")),
			readStringList(rs.getString("expected_concepts")),
			readRubric(rs.getString("rubric")),
			rs.getInt("version")
		);
	}

	private QuestionRetrievalCandidate mapRetrievalCandidate(ResultSet rs, int rowNum) throws SQLException {
		return new QuestionRetrievalCandidate(
			mapQuestion(rs, rowNum),
			rs.getDouble("distance"),
			rs.getString("embedded_text_hash")
		);
	}

	private List<String> readStringList(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyList();
		}
		try {
			return objectMapper.readValue(json, STRING_LIST);
		} catch (Exception ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to parse string list JSON", ex));
		}
	}

	private Map<String, Integer> readRubric(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyMap();
		}
		try {
			return objectMapper.readValue(json, RUBRIC_MAP);
		} catch (Exception ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to parse rubric JSON", ex));
		}
	}

	private String normalizeMode(String mode) {
		return mode.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	private String normalizeSeniority(String seniority) {
		return seniority.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

}
