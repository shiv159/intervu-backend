package com.intervu;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTests {

	@Test
	void schemaAndSeedFilesDefineTheBootstrapDatabase() throws Exception {
		var schema = Files.readString(Path.of("src/main/resources/schema.sql"));
		var data = Files.readString(Path.of("src/main/resources/data.sql"));

		for (var table : new String[] { "questions", "interview_sessions", "interview_interactions",
				"evaluations", "session_events" }) {
			assertThat(schema).contains("CREATE TABLE IF NOT EXISTS " + table);
		}

		for (var table : new String[] { "question_embeddings", "evaluation_runs", "analytics_snapshots" }) {
			assertThat(schema).doesNotContain("CREATE TABLE IF NOT EXISTS " + table);
		}

		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_questions_status");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_questions_tags");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_interview_sessions_owner_id");
		assertThat(schema).contains("CREATE UNIQUE INDEX IF NOT EXISTS idx_interview_interactions_idempotency_key");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_evaluations_session_id");
		assertThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_session_events_session_id_version");

		assertThat(data).contains("Two Sum in Java");
		assertThat(data).contains("Design a URL Shortener");
		assertThat(data).contains("Tell Me About a Production Incident");
		assertThat(data).contains("ON CONFLICT (id) DO NOTHING");
		assertThat(data).contains("'CODE'");
		assertThat(data).contains("'SYSTEM_DESIGN'");
		assertThat(data).contains("'CONVERSATIONAL'");
	}

}
