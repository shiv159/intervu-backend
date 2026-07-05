package com.intervu.questionbank.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QuestionEmbeddingRepository {

	public record EmbeddingMetadata(UUID questionId, String model, String embeddedTextHash, Instant updatedAt) {
	}

	private final JdbcTemplate jdbcTemplate;

	public QuestionEmbeddingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void upsertEmbedding(UUID questionId, String embeddingLiteral, String model, String embeddedTextHash) {
		jdbcTemplate.update(
			"""
				INSERT INTO question_embeddings (
					question_id, embedding, model, embedded_text_hash, updated_at
				)
				VALUES (?, ?::vector, ?, ?, CURRENT_TIMESTAMP)
				ON CONFLICT (question_id)
				DO UPDATE SET
					embedding = EXCLUDED.embedding,
					model = EXCLUDED.model,
					embedded_text_hash = EXCLUDED.embedded_text_hash,
					updated_at = CURRENT_TIMESTAMP
				""",
			questionId,
			embeddingLiteral,
			model,
			embeddedTextHash
		);
	}

	public Optional<EmbeddingMetadata> findMetadata(UUID questionId) {
		List<EmbeddingMetadata> rows = jdbcTemplate.query(
			"""
				SELECT question_id, model, embedded_text_hash, updated_at
				FROM question_embeddings
				WHERE question_id = ?
				""",
			this::mapMetadata,
			questionId
		);
		return rows.stream().findFirst();
	}

	private EmbeddingMetadata mapMetadata(ResultSet rs, int rowNum) throws SQLException {
		return new EmbeddingMetadata(
			rs.getObject("question_id", UUID.class),
			rs.getString("model"),
			rs.getString("embedded_text_hash"),
			rs.getTimestamp("updated_at").toInstant()
		);
	}
}
