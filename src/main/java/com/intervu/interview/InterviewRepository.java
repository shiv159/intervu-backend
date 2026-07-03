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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.intervu.interview.InterviewDtos.EvaluationRow;
import static com.intervu.interview.InterviewDtos.InteractionRow;
import static com.intervu.interview.InterviewDtos.SessionRow;
import static com.intervu.interview.InterviewDtos.SessionEventRow;

@Repository
public class InterviewRepository {

	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};
	private static final TypeReference<Map<String, Integer>> INTEGER_MAP = new TypeReference<>() {
	};
	private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
	};

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public InterviewRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public void insertSession(SessionRow session) {
		jdbcTemplate.update(
			"""
				INSERT INTO interview_sessions (
					id, owner_id, target_role, state, mode, seniority, difficulty,
					skills, focus_areas, current_question_id, state_version
				)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
				""",
			session.id(),
			session.ownerId(),
			session.targetRole(),
			session.state(),
			session.mode(),
			session.seniority(),
			session.difficulty(),
			writeJson(session.skills()),
			writeJson(session.focusAreas()),
			session.currentQuestionId(),
			session.stateVersion()
		);
	}

	public SessionRow loadSession(UUID sessionId) {
		List<SessionRow> sessions = jdbcTemplate.query(
			"""
				SELECT id, owner_id, target_role, state, mode, seniority, difficulty,
				       skills, focus_areas, current_question_id, state_version
				FROM interview_sessions
				WHERE id = ?
				""",
			this::mapSession,
			sessionId
		);
		return sessions.stream()
			.findFirst()
			.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
				org.springframework.http.HttpStatus.NOT_FOUND,
				"Interview session not found"
			));
	}

	public void updateSessionState(UUID sessionId, String state) {
		jdbcTemplate.update(
			"UPDATE interview_sessions SET state = ?, state_version = state_version + 1, updated_at = now() WHERE id = ?",
			state, sessionId
		);
	}

	public void updateSessionCurrentQuestion(UUID sessionId, UUID questionId) {
		jdbcTemplate.update(
			"UPDATE interview_sessions SET current_question_id = ?, state_version = state_version + 1, updated_at = now() WHERE id = ?",
			questionId, sessionId
		);
	}

	public void insertInteraction(UUID interactionId, UUID sessionId, UUID questionId, String idempotencyKey, String interactionType, String payload) {
		jdbcTemplate.update(
			"""
				INSERT INTO interview_interactions (
					id, session_id, question_id, idempotency_key, interaction_type, payload
				)
				VALUES (?, ?, ?, ?, ?, ?::jsonb)
				""",
			interactionId,
			sessionId,
			questionId,
			idempotencyKey,
			interactionType,
			payload
		);
	}

	public Optional<InteractionRow> findInteractionByIdempotencyKey(UUID sessionId, String idempotencyKey) {
		return jdbcTemplate.query(
			"""
				SELECT id, session_id, question_id, idempotency_key, interaction_type, payload
				FROM interview_interactions
				WHERE session_id = ? AND idempotency_key = ?
				""",
			this::mapInteraction,
			sessionId,
			idempotencyKey
		).stream().findFirst();
	}

	public List<UUID> findAnsweredQuestionIdsBySessionId(UUID sessionId) {
		return jdbcTemplate.query(
			"""
				SELECT DISTINCT question_id
				FROM interview_interactions
				WHERE session_id = ? AND interaction_type = 'ANSWER' AND question_id IS NOT NULL
				""",
			(rs, rowNum) -> rs.getObject("question_id", UUID.class),
			sessionId
		);
	}

	public void insertEvaluation(UUID evaluationId, UUID sessionId, UUID interactionId, int score, Map<String, Integer> rubricScores, List<String> strengths, List<String> gaps, String followUpQuestion, String model, String provider, Long latencyMs, Double cost) {
		jdbcTemplate.update(
			"""
				INSERT INTO evaluations (
					id, session_id, interaction_id, score, rubric_scores, strengths, gaps, follow_up_question,
					model, provider, latency_ms, cost
				)
				VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
				""",
			evaluationId,
			sessionId,
			interactionId,
			score,
			writeJson(rubricScores),
			writeJson(strengths),
			writeJson(gaps),
			followUpQuestion,
			model,
			provider,
			latencyMs,
			cost
		);
	}

	public Optional<EvaluationRow> findEvaluationByInteractionId(UUID interactionId) {
		List<EvaluationRow> evaluations = jdbcTemplate.query(
			"""
				SELECT id, session_id, interaction_id, score, rubric_scores, strengths, gaps, follow_up_question, model, provider, latency_ms, cost
				FROM evaluations
				WHERE interaction_id = ?
				ORDER BY created_at DESC
				LIMIT 1
				""",
			this::mapEvaluation,
			interactionId
		);
		return evaluations.stream().findFirst();
	}

	public Optional<EvaluationRow> findLatestEvaluationBySessionId(UUID sessionId) {
		List<EvaluationRow> evaluations = jdbcTemplate.query(
			"""
				SELECT id, session_id, interaction_id, score, rubric_scores, strengths, gaps, follow_up_question, model, provider, latency_ms, cost
				FROM evaluations
				WHERE session_id = ?
				ORDER BY created_at DESC
				LIMIT 1
				""",
			this::mapEvaluation,
			sessionId
		);
		return evaluations.stream().findFirst();
	}

	public void insertEvent(UUID sessionId, String eventType, String payload) {
		Integer nextVersion = jdbcTemplate.queryForObject(
			"""
				SELECT COALESCE(MAX(event_version), 0) + 1
				FROM session_events
				WHERE session_id = ?
				""",
			Integer.class,
			sessionId
		);
		jdbcTemplate.update(
			"""
				INSERT INTO session_events (id, session_id, event_version, event_type, payload)
				VALUES (?, ?, ?, ?, ?::jsonb)
				""",
			UUID.randomUUID(),
			sessionId,
			nextVersion == null ? 1 : nextVersion,
			eventType,
			payload
		);
	}

	public List<SessionEventRow> findEventsAfter(UUID sessionId, long afterVersion) {
		return jdbcTemplate.query(
			"""
				SELECT event_version, event_type, payload
				FROM session_events
				WHERE session_id = ? AND event_version > ?
				ORDER BY event_version ASC
				""",
			this::mapEvent,
			sessionId,
			afterVersion
		);
	}

	private SessionRow mapSession(ResultSet rs, int rowNum) throws SQLException {
		return new SessionRow(
			rs.getObject("id", UUID.class),
			rs.getString("owner_id"),
			rs.getString("target_role"),
			rs.getString("state"),
			rs.getString("mode"),
			rs.getString("seniority"),
			rs.getString("difficulty"),
			readStringList(rs.getString("skills")),
			readStringList(rs.getString("focus_areas")),
			rs.getObject("current_question_id", UUID.class),
			rs.getLong("state_version")
		);
	}

	private InteractionRow mapInteraction(ResultSet rs, int rowNum) throws SQLException {
		return new InteractionRow(
			rs.getObject("id", UUID.class),
			rs.getObject("session_id", UUID.class),
			rs.getObject("question_id", UUID.class),
			rs.getString("idempotency_key"),
			rs.getString("payload")
		);
	}

	private EvaluationRow mapEvaluation(ResultSet rs, int rowNum) throws SQLException {
		Double cost = rs.getObject("cost") != null ? rs.getDouble("cost") : null;
		return new EvaluationRow(
			rs.getObject("id", UUID.class),
			rs.getObject("session_id", UUID.class),
			rs.getObject("interaction_id", UUID.class),
			rs.getInt("score"),
			readIntegerMap(rs.getString("rubric_scores")),
			readStringList(rs.getString("strengths")),
			readStringList(rs.getString("gaps")),
			rs.getString("follow_up_question"),
			rs.getString("model"),
			rs.getString("provider"),
			rs.getObject("latency_ms", Long.class),
			cost
		);
	}

	private SessionEventRow mapEvent(ResultSet rs, int rowNum) throws SQLException {
		return new SessionEventRow(
			rs.getLong("event_version"),
			rs.getString("event_type"),
			readObjectMap(rs.getString("payload"))
		);
	}

	private List<String> readStringList(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyList();
		}
		try {
			return objectMapper.readValue(json, STRING_LIST);
		} catch (Exception ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to parse string JSON array", ex));
		}
	}

	private Map<String, Integer> readIntegerMap(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyMap();
		}
		try {
			return objectMapper.readValue(json, INTEGER_MAP);
		} catch (Exception ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to parse integer JSON map", ex));
		}
	}

	private Map<String, Object> readObjectMap(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyMap();
		}
		try {
			return objectMapper.readValue(json, OBJECT_MAP);
		} catch (Exception ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to parse event JSON payload", ex));
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to serialise JSON payload", ex));
		}
	}

}
