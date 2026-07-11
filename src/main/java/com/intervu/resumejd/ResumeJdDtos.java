package com.intervu.resumejd;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResumeJdDtos {

	private ResumeJdDtos() {
	}

	public record CreateJdRequest(String sourceText) {
	}

	public record ResumeExtractResponse(
		UUID id,
		String sourceFilename,
		String extractedText,
		List<String> skills,
		List<String> focusAreas,
		List<String> claims,
		String targetRole,
		String seniority
	) {
	}

	public record JdExtractResponse(
		UUID id,
		String sourceText,
		List<String> requirements,
		List<String> technologies,
		List<String> responsibilities,
		String seniority
	) {
	}

	public record ResumeExtract(
		UUID id,
		String ownerId,
		String sourceFilename,
		String extractedText,
		List<String> skills,
		List<String> focusAreas,
		List<String> claims,
		String targetRole,
		String seniority,
		String parserVersion,
		boolean deleted,
		Instant createdAt
	) {
	}

	public record JdExtract(
		UUID id,
		String ownerId,
		String sourceText,
		List<String> requirements,
		List<String> technologies,
		List<String> responsibilities,
		String seniority,
		String extractorVersion,
		boolean deleted,
		Instant createdAt
	) {
	}

}
