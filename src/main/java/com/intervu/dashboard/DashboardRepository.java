package com.intervu.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.intervu.dashboard.DashboardDtos.DashboardSessionSummary;
import static com.intervu.dashboard.DashboardDtos.RoundFeedback;
import static com.intervu.dashboard.DashboardDtos.SessionFeedbackResponse;
import static com.intervu.dashboard.DashboardDtos.TopicMastery;

@Repository
public class DashboardRepository {

	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
	private static final TypeReference<Map<String, Integer>> INTEGER_MAP = new TypeReference<>() {};
	private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public DashboardRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public List<DashboardSessionSummary> findSessionsByOwner(String ownerId, int page, int size) {
		int offset = page * size;
		return jdbcTemplate.query(
			"""
				SELECT s.id, s.target_role, s.mode, s.seniority, s.state, s.state_version,
				       s.created_at, s.updated_at,
				       (SELECT MAX(e.score) FROM evaluations e WHERE e.session_id = s.id) AS overall_score
				FROM interview_sessions s
				WHERE s.owner_id = ?
				ORDER BY s.created_at DESC
				LIMIT ? OFFSET ?
				""",
			this::mapDashboardSummary,
			ownerId,
			size,
			offset
		);
	}

	public List<RoundFeedback> findEvaluationsBySessionId(UUID sessionId) {
		return jdbcTemplate.query(
			"""
				SELECT e.score, e.rubric_scores, e.strengths, e.gaps,
				       COALESCE(q.title, 'Unknown Question') AS question_title,
				       COALESCE(q.id, e.interaction_id) AS question_id,
				       COALESCE(q.mode, 'UNKNOWN') AS mode
				FROM evaluations e
				JOIN interview_interactions ii ON ii.id = e.interaction_id
				LEFT JOIN questions q ON q.id = ii.question_id
				WHERE e.session_id = ?
				ORDER BY e.created_at ASC
				""",
			this::mapRoundFeedback,
			sessionId
		);
	}

	public List<String> findSessionTags(UUID sessionId) {
		List<String> tags = jdbcTemplate.query(
			"""
				SELECT DISTINCT jsonb_array_elements_text(s.skills) AS tag
				FROM interview_sessions s
				WHERE s.id = ?
				UNION
				SELECT DISTINCT jsonb_array_elements_text(q.tags) AS tag
				FROM interview_interactions ii
				JOIN questions q ON q.id = ii.question_id
				WHERE ii.session_id = ? AND ii.interaction_type = 'ANSWER'
				""",
			(rs, rowNum) -> rs.getString("tag"),
			sessionId,
			sessionId
		);
		return tags != null ? tags : Collections.emptyList();
	}

	public void insertAnalyticsSnapshot(UUID snapshotId, UUID sessionId, String metrics) {
		jdbcTemplate.update(
			"""
				INSERT INTO analytics_snapshots (id, session_id, metrics)
				VALUES (?, ?, ?::jsonb)
				""",
			snapshotId,
			sessionId,
			metrics
		);
	}

	public void deleteSessionData(UUID sessionId, String ownerId) {
		// Verify ownership first
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM interview_sessions WHERE id = ? AND owner_id = ?",
			Integer.class,
			sessionId,
			ownerId
		);
		if (count == null || count == 0) {
			throw new org.springframework.web.server.ResponseStatusException(
				org.springframework.http.HttpStatus.NOT_FOUND,
				"Session not found or access denied"
			);
		}
		// Cascade deletes via FK constraints: analytics_snapshots, session_events,
		// evaluations, interview_interactions, interview_sessions
		jdbcTemplate.update("DELETE FROM analytics_snapshots WHERE session_id = ?", sessionId);
		jdbcTemplate.update("DELETE FROM session_events WHERE session_id = ?", sessionId);
		jdbcTemplate.update("DELETE FROM evaluations WHERE session_id = ?", sessionId);
		jdbcTemplate.update("DELETE FROM interview_interactions WHERE session_id = ?", sessionId);
		jdbcTemplate.update("DELETE FROM interview_sessions WHERE id = ?", sessionId);
	}

	public Optional<Instant> findSessionCreatedAt(UUID sessionId) {
		return jdbcTemplate.query(
			"SELECT created_at FROM interview_sessions WHERE id = ?",
			(rs, rowNum) -> toInstant(rs.getTimestamp("created_at")),
			sessionId
		).stream().findFirst();
	}

	public Optional<Instant> findSessionCompletedAt(UUID sessionId) {
		List<Instant> results = jdbcTemplate.query(
			"""
				SELECT created_at FROM session_events
				WHERE session_id = ? AND event_type = 'SESSION_COMPLETED'
				ORDER BY created_at DESC LIMIT 1
				""",
			(rs, rowNum) -> toInstant(rs.getTimestamp("created_at")),
			sessionId
		);
		return results.stream().findFirst();
	}

	private DashboardSessionSummary mapDashboardSummary(ResultSet rs, int rowNum) throws SQLException {
		Integer overallScore = rs.getObject("overall_score") != null ? rs.getInt("overall_score") : null;
		return new DashboardSessionSummary(
			rs.getObject("id", UUID.class),
			rs.getString("target_role"),
			rs.getString("mode"),
			rs.getString("seniority"),
			rs.getString("state"),
			overallScore,
			deriveSummaryText(rs.getString("state"), overallScore),
			rs.getLong("state_version"),
			toInstant(rs.getTimestamp("created_at")),
			toInstant(rs.getTimestamp("updated_at"))
		);
	}

	private RoundFeedback mapRoundFeedback(ResultSet rs, int rowNum) throws SQLException {
		return new RoundFeedback(
			rs.getObject("question_id", UUID.class),
			rs.getString("question_title"),
			rs.getInt("score"),
			readIntegerMap(rs.getString("rubric_scores")),
			readStringList(rs.getString("strengths")),
			readStringList(rs.getString("gaps")),
			rs.getString("mode")
		);
	}

	private String deriveSummaryText(String state, Integer score) {
		if (state == null) return "Not started";
		return switch (state) {
			case "CREATED" -> "Not started";
			case "IN_PROGRESS" -> "In progress";
			case "WAITING_EVALUATION" -> "Awaiting evaluation";
			case "EVALUATED" -> "Ready to continue";
			case "COMPLETED" -> score != null ? mapScoreToReadiness(score) : "Completed";
			case "EXPIRED" -> "Expired";
			case "ABANDONED" -> "Abandoned";
			default -> state;
		};
	}

	private String mapScoreToReadiness(int score) {
		if (score >= 80) return "Strong";
		if (score >= 60) return "Solid";
		if (score >= 40) return "Mixed";
		return "Needs Work";
	}

	private static Instant toInstant(Timestamp ts) {
		return ts != null ? ts.toInstant() : null;
	}

	private List<String> readStringList(String json) {
		if (json == null || json.isBlank()) return Collections.emptyList();
		try {
			return objectMapper.readValue(json, STRING_LIST);
		} catch (Exception ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to parse string JSON array", ex));
		}
	}

	private Map<String, Integer> readIntegerMap(String json) {
		if (json == null || json.isBlank()) return Collections.emptyMap();
		try {
			return objectMapper.readValue(json, INTEGER_MAP);
		} catch (Exception ex) {
			throw new UncheckedIOException(new java.io.IOException("Failed to parse integer JSON map", ex));
		}
	}
}