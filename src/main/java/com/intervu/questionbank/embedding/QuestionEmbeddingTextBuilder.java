package com.intervu.questionbank.embedding;

import com.intervu.questionbank.QuestionDef;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class QuestionEmbeddingTextBuilder {

	public String buildCanonicalText(QuestionDef def) {
		return String.join("\n",
			"Title: " + safe(def.title()),
			"Prompt: " + safe(def.prompt()),
			"Mode: " + safe(def.mode()),
			"Difficulty: " + safe(def.difficulty()),
			"Seniority: " + safe(def.seniority()),
			"Tags: " + join(def.tags()),
			"Expected Concepts: " + join(def.expectedConcepts())
		);
	}

	public String hashCanonicalText(String canonicalText) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(canonicalText.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	public String toPgVectorLiteral(List<Double> embedding) {
		return embedding.stream()
			.map(value -> value == null ? "0" : stripTrailingZeros(value))
			.collect(Collectors.joining(",", "[", "]"));
	}

	private String join(List<String> values) {
		if (values == null || values.isEmpty()) {
			return "";
		}
		return values.stream()
			.filter(value -> value != null && !value.isBlank())
			.map(value -> value.trim())
			.collect(Collectors.joining(", "));
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private String stripTrailingZeros(Double value) {
		String raw = value.toString();
		if (raw.indexOf('.') < 0) {
			return raw;
		}
		return raw.replaceAll("0+$", "").replaceAll("\\.$", "");
	}
}
