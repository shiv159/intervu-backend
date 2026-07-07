package com.intervu;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTests {

	@Test
	void schemaAndSeedFilesDefineTheBootstrapDatabase() throws Exception {
		var applicationProperties = Files.readString(Path.of("src/main/resources/application.properties"));
		var schema = Files.readString(Path.of("src/main/resources/schema.sql"));
		var data = Files.readString(Path.of("src/main/resources/data.sql"));

		assertThat(applicationProperties.lines().filter(line -> line.startsWith("spring.sql.init.mode=")))
			.hasSize(1);
		assertThat(applicationProperties)
			.contains("spring.sql.init.mode=${SPRING_SQL_INIT_MODE:always}");

		for (var table : new String[] { "questions", "interview_sessions", "interview_interactions",
				"evaluations", "session_events", "question_embeddings" }) {
			assertThat(schema).contains("CREATE TABLE IF NOT EXISTS " + table);
		}

		for (var table : new String[] { "evaluation_runs", "analytics_snapshots" }) {
			assertThat(schema).doesNotContain("CREATE TABLE IF NOT EXISTS " + table);
		}

		assertThat(schema).contains("current_question_version INT");
		assertThat(schema).contains("question_version INT");
		assertThat(schema).contains("CREATE EXTENSION IF NOT EXISTS vector");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_questions_status");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_questions_tags");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_interview_sessions_owner_id");
		assertThat(schema).contains("CREATE UNIQUE INDEX IF NOT EXISTS idx_interview_interactions_idempotency_key");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_evaluations_session_id");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_session_events_session_id_version");

		assertThat(schema).contains("ALTER TABLE interview_sessions");
		assertThat(schema).contains("ADD COLUMN IF NOT EXISTS current_question_version INT");
		assertThat(schema).contains("ALTER TABLE interview_interactions");
		assertThat(schema).contains("ADD COLUMN IF NOT EXISTS question_version INT");

		assertThat(data).contains("Two Sum in Java");
		assertThat(data).contains("Design a URL Shortener");
		assertThat(data).contains("Tell Me About a Production Incident");
		assertThat(data).contains("ON CONFLICT (id) DO NOTHING");
		assertThat(data).contains("'CODE'");
		assertThat(data).contains("'SYSTEM_DESIGN'");
		assertThat(data).contains("'CONVERSATIONAL'");
	}

}
