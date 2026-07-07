package com.intervu.interview;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Validates at startup that all required columns exist on the database tables.
 * <p>
 * This prevents the application from starting in a broken state when the database
 * schema has drifted from what the code expects.  If any required column is missing
 * the application will fail fast with a clear log message instead of producing
 * opaque 500 errors at request time.
 */
@Component
public class SchemaValidator {

	private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

	/** table → required columns that the code queries */
	private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
		"interview_sessions", List.of(
			"id", "owner_id", "target_role", "state", "mode", "seniority", "difficulty",
			"skills", "focus_areas", "current_question_id", "current_question_version",
			"state_version", "created_at", "updated_at"
		),
		"interview_interactions", List.of(
			"id", "session_id", "question_id", "question_version",
			"idempotency_key", "interaction_type", "payload", "created_at"
		)
	);

	private final JdbcTemplate jdbcTemplate;

	public SchemaValidator(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostConstruct
	void validateSchema() {
		log.info("Running database schema validation ...");

		try {
			for (var entry : REQUIRED_COLUMNS.entrySet()) {
				String table = entry.getKey();
				List<String> required = entry.getValue();

				List<String> actual = jdbcTemplate.queryForList(
					"SELECT column_name FROM information_schema.columns " +
						"WHERE table_name = ?",
					table
				).stream()
					.map(row -> (String) row.get("column_name"))
					.toList();

				List<String> missing = required.stream()
					.filter(col -> !actual.contains(col))
					.collect(Collectors.toList());

				if (!missing.isEmpty()) {
					String message = String.format(
						"SCHEMA VALIDATION FAILED: Table '%s' is missing columns %s. " +
							"The application cannot start. Run the latest schema.sql to add the missing columns.",
						table, missing
					);
					log.error(message);
					throw new IllegalStateException(message);
				}
			}

			log.info("Database schema validation passed — all required columns present.");
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			log.warn("Database schema validation skipped — could not connect to database: {}", e.getMessage());
		}
	}
}