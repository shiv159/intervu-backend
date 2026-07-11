package com.intervu.resumejd;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.intervu.resumejd.ResumeJdDtos.JdExtract;
import static com.intervu.resumejd.ResumeJdDtos.ResumeExtract;

@Service
public class ResumeJdService {

	private static final String PARSER_VERSION = "tika-1";
	private static final String EXTRACTOR_VERSION = "tika-1";
	private static final int MAX_TEXT_CHARS = 200_000;

	private final Tika tika = new Tika();

	private static final Set<String> SKILL_KEYWORDS = Set.of(
		"java", "kotlin", "scala", "python", "golang", "rust", "c++", "c#", "javascript", "typescript",
		"node", "node.js", "react", "angular", "vue", "spring", "spring boot", "hibernate", "jpa",
		"kafka", "rabbitmq", "redis", "memcached", "postgresql", "mysql", "mongodb", "cassandra", "dynamodb",
		"elasticsearch", "neo4j", "graphql", "rest", "grpc", "protobuf", "docker", "kubernetes", "terraform",
		"aws", "gcp", "azure", "lambda", "ecs", "eks", "s3", "sqs", "microservices", "distributed systems",
		"event-driven", "ci/cd", "jenkins", "git", "maven", "gradle", "junit", "mockito", "tdd",
		"sql", "nosql", "machine learning", "deep learning", "pytorch", "tensorflow", "spark", "flink",
		"prometheus", "grafana", "opentelemetry", "oauth", "jwt", "encryption"
	);

	private static final Set<String> FOCUS_KEYWORDS = Set.of(
		"concurrency", "parallelism", "scalability", "scaling", "performance", "optimization", "throughput",
		"latency", "reliability", "resilience", "availability", "consistency", "caching",
		"privacy", "data modeling", "system design", "architecture", "fault tolerance", "event-driven"
	);

	private static final Pattern BULLET = Pattern.compile("^\\s*([\\-\\u2022\\*\\u25aa\\u2013\\u2014]|\\d+[\\.\\)])\\s+");

	public String extractText(MultipartFile file) throws IOException {
		try (InputStream in = file.getInputStream()) {
			String text = tika.parseToString(in);
			if (text == null) {
				return "";
			}
			return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
		} catch (TikaException ex) {
			throw new IOException("Failed to parse document: " + ex.getMessage(), ex);
		}
	}

	public ResumeExtract parseResume(UUID id, String ownerId, String filename, String text) {
		String lower = (text == null ? "" : text).toLowerCase(Locale.ROOT);
		Set<String> skills = detectKeywords(lower, SKILL_KEYWORDS);
		Set<String> focus = detectKeywords(lower, FOCUS_KEYWORDS);
		return new ResumeExtract(
			id,
			ownerId,
			filename,
			text == null ? "" : text,
			new ArrayList<>(skills),
			new ArrayList<>(focus),
			extractClaims(text),
			detectTargetRole(text),
			detectSeniority(lower),
			PARSER_VERSION,
			false,
			Instant.now()
		);
	}

	public JdExtract parseJd(UUID id, String ownerId, String text) {
		String safe = text == null ? "" : text;
		String lower = safe.toLowerCase(Locale.ROOT);
		Set<String> tech = detectKeywords(lower, SKILL_KEYWORDS);
		return new JdExtract(
			id,
			ownerId,
			safe,
			extractByKeyword(safe, List.of("require", "must have", "should have", "qualification")),
			new ArrayList<>(tech),
			extractByKeyword(safe, List.of("responsib", "you will", "will be", "will own", "own ", "build", "design", "develop")),
			detectSeniority(lower),
			EXTRACTOR_VERSION,
			false,
			Instant.now()
		);
	}

	private Set<String> detectKeywords(String lowerText, Set<String> keywords) {
		Set<String> found = new LinkedHashSet<>();
		for (String keyword : keywords) {
			if (lowerText.contains(keyword)) {
				found.add(keyword);
			}
		}
		return found;
	}

	private List<String> extractClaims(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> claims = new ArrayList<>();
		for (String line : text.split("\\r?\\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			boolean bullet = BULLET.matcher(trimmed).find();
			boolean metric = trimmed.matches(".*\\b(\\d+%|\\d+\\s*(x|times|years?)|increased|reduced|improved|led|built|shipped)\\b.*");
			if (bullet || metric) {
				claims.add(trimmed);
			}
			if (claims.size() >= 10) {
				break;
			}
		}
		return claims;
	}

	private List<String> extractByKeyword(String text, List<String> keywords) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		List<String> matched = new ArrayList<>();
		for (String line : text.split("\\r?\\n")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			String lower = trimmed.toLowerCase(Locale.ROOT);
			if (keywords.stream().anyMatch(lower::contains)) {
				matched.add(trimmed);
			}
			if (matched.size() >= 10) {
				break;
			}
		}
		return matched;
	}

	private String detectSeniority(String lowerText) {
		for (String token : List.of("principal", "staff", "lead", "senior", "mid", "junior")) {
			if (lowerText.contains(token)) {
				return token.toUpperCase(Locale.ROOT);
			}
		}
		return null;
	}

	private String detectTargetRole(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		for (String line : text.split("\\r?\\n")) {
			String trimmed = line.trim();
			if (trimmed.length() < 3 || trimmed.length() > 80) {
				continue;
			}
			String lower = trimmed.toLowerCase(Locale.ROOT);
			if (lower.contains("engineer") || lower.contains("developer") || lower.contains("scientist")
				|| lower.contains("architect") || lower.contains("manager") || lower.contains("designer")) {
				return trimmed;
			}
		}
		return null;
	}
}
