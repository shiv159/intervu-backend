package com.intervu.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

	private final JdbcTemplate jdbcTemplate;

	public UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<UserRecord> findByEmail(String email) {
		List<UserRecord> rows = jdbcTemplate.query(
			"""
				SELECT id, email, password_hash, created_at
				FROM users
				WHERE email = ?
				""",
			this::mapUser,
			email
		);
		return rows.stream().findFirst();
	}

	public Optional<UserRecord> findById(String id) {
		List<UserRecord> rows = jdbcTemplate.query(
			"""
				SELECT id, email, password_hash, created_at
				FROM users
				WHERE id = ?
				""",
			this::mapUser,
			id
		);
		return rows.stream().findFirst();
	}

	public UserRecord insert(String email, String passwordHash) {
		String id = UUID.randomUUID().toString();
		Instant createdAt = Instant.now();
		Timestamp createdAtTs = Timestamp.from(createdAt);
		jdbcTemplate.update(
			"""
				INSERT INTO users (id, email, password_hash, created_at)
				VALUES (?, ?, ?, ?)
				""",
			id,
			email,
			passwordHash,
			createdAtTs
		);
		return new UserRecord(id, email, passwordHash, createdAt);
	}

	private UserRecord mapUser(ResultSet rs, int rowNum) throws SQLException {
		return new UserRecord(
			rs.getString("id"),
			rs.getString("email"),
			rs.getString("password_hash"),
			rs.getObject("created_at", Instant.class)
		);
	}

	public record UserRecord(
		String id,
		String email,
		String passwordHash,
		Instant createdAt
	) {
	}
}
