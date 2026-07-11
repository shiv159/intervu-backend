package com.intervu.resumejd;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.intervu.resumejd.ResumeJdDtos.JdExtract;
import static com.intervu.resumejd.ResumeJdDtos.ResumeExtract;

@Repository
public class ResumeJdRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public ResumeJdRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public void insertResume(ResumeExtract extract) {
		jdbcTemplate.update(
			"""
				INSERT INTO resume_extracts (
					id, owner_id, source_filename, extracted_text, skills, focus_areas, claims,
					target_role, seniority, parser_version, deleted, created_at
				)
				VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
				""",
			extract.id(),
			extract.ownerId(),
			extract.sourceFilename(),
			extract.extractedText(),
			writeJson(extract.skills()),
			writeJson(extract.focusAreas()),
			writeJson(extract.claims()),
			extract.targetRole(),
			extract.seniority(),
			extract.parserVersion(),
			extract.deleted(),
			extract.createdAt()
		);
	}

	public void insertJd(JdExtract extract) {
		jdbcTemplate.update(
			"""
				INSERT INTO jd_extracts (
					id, owner_id, source_text, requirements, technologies, responsibilities,
					seniority, extractor_version, deleted, created_at
				)
				VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
				""",
			extract.id(),
			extract.ownerId(),
			extract.sourceText(),
			writeJson(extract.requirements()),
			writeJson(extract.technologies()),
			writeJson(extract.responsibilities()),
			extract.seniority(),
			extract.extractorVersion(),
			extract.deleted(),
			extract.createdAt()
		);
	}

	public Optional<ResumeExtract> findResumeById(UUID id) {
		return jdbcTemplate.query(
			"""
				SELECT id, owner_id, source_filename, extracted_text, skills, focus_areas, claims,
				       target_role, seniority, parser_version, deleted, created_at
				FROM resume_extracts
				WHERE id = ?
				""",
			this::mapResume,
			id
		).stream().findFirst();
	}

	public Optional<JdExtract> findJdById(UUID id) {
		return jdbcTemplate.query(
			"""
				SELECT id, owner_id, source_text, requirements, technologies, responsibilities,
				       seniority, extractor_version, deleted, created_at
				FROM jd_extracts
				WHERE id = ?
				""",
			this::mapJd,
			id
		).stream().findFirst();
	}

	public Optional<ResumeExtract> findLatestResume(String ownerId) {
		return jdbcTemplate.query(
			"""
				SELECT id, owner_id, source_filename, extracted_text, skills, focus_areas, claims,
				       target_role, seniority, parser_version, deleted, created_at
				FROM resume_extracts
				WHERE owner_id = ? AND deleted = FALSE
				ORDER BY created_at DESC
				LIMIT 1
				""",
			this::mapResume,
			ownerId
		).stream().findFirst();
	}

	public Optional<JdExtract> findLatestJd(String ownerId) {
		return jdbcTemplate.query(
			"""
				SELECT id, owner_id, source_text, requirements, technologies, responsibilities,
				       seniority, extractor_version, deleted, created_at
				FROM jd_extracts
				WHERE owner_id = ? AND deleted = FALSE
				ORDER BY created_at DESC
				LIMIT 1
				""",
			this::mapJd,
			ownerId
		).stream().findFirst();
	}

	public void softDeleteResume(UUID id, String ownerId) {
		jdbcTemplate.update("UPDATE resume_extracts SET deleted = TRUE WHERE id = ? AND owner_id = ?", id, ownerId);
	}

	public void softDeleteJd(UUID id, String ownerId) {
		jdbcTemplate.update("UPDATE jd_extracts SET deleted = TRUE WHERE id = ? AND owner_id = ?", id, ownerId);
	}

	private ResumeExtract mapResume(ResultSet rs, int rowNum) throws SQLException {
		return new ResumeExtract(
			rs.getObject("id", UUID.class),
			rs.getString("owner_id"),
			rs.getString("source_filename"),
			rs.getString("extracted_text"),
			readStringList(rs.getString("skills")),
			readStringList(rs.getString("focus_areas")),
			readStringList(rs.getString("claims")),
			rs.getString("target_role"),
			rs.getString("seniority"),
			rs.getString("parser_version"),
			rs.getBoolean("deleted"),
			rs.getObject("created_at", Instant.class)
		);
	}

	private JdExtract mapJd(ResultSet rs, int rowNum) throws SQLException {
		return new JdExtract(
			rs.getObject("id", UUID.class),
			rs.getString("owner_id"),
			rs.getString("source_text"),
			readStringList(rs.getString("requirements")),
			readStringList(rs.getString("technologies")),
			readStringList(rs.getString("responsibilities")),
			rs.getString("seniority"),
			rs.getString("extractor_version"),
			rs.getBoolean("deleted"),
			rs.getObject("created_at", Instant.class)
		);
	}

	private List<String> readStringList(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new tools.jackson.core.type.TypeReference<List<String>>() {
			});
		} catch (Exception ex) {
			return List.of();
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to serialise JSON payload", ex);
		}
	}
}
