package com.intervu;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTests {

	@Test
	void initialMigrationDefinesMvpSchemaAndSeeds() throws Exception {
		var sql = Files.readString(Path.of("src/main/resources/db/migration/V1__mvp_schema.sql"));

		for (var table : new String[] { "questions", "question_embeddings", "interview_sessions",
				"interview_interactions", "evaluations", "evaluation_runs", "session_events",
				"analytics_snapshots" }) {
			assertThat(sql).contains("CREATE TABLE " + table);
		}

		assertThat(sql).contains("CREATE EXTENSION IF NOT EXISTS vector");
		assertThat(sql).contains("idx_questions_status");
		assertThat(sql).contains("idx_questions_tags");
		assertThat(sql).contains("idx_interview_sessions_owner_id");
		assertThat(sql).contains("idx_session_events_session_id_version");
		assertThat(sql).contains("idx_interview_interactions_idempotency_key");
		assertThat(sql).contains("idx_evaluations_session_id");
		assertThat(sql).contains("'CODE'");
		assertThat(sql).contains("'SYSTEM_DESIGN'");
		assertThat(sql).contains("'CONVERSATIONAL'");
	}

}
