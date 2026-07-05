package com.intervu.interview;

import com.intervu.questionbank.QuestionDef;
import com.intervu.questionbank.embedding.EmbeddingClient;
import com.intervu.questionbank.embedding.EmbeddingResult;
import com.intervu.questionbank.embedding.QuestionEmbeddingTextBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intervu.interview.InterviewDtos.QuestionPayload;

@Service
public class QuestionRetrievalService {

	private final QuestionRepository questionRepository;
	private final EmbeddingClient embeddingClient;
	private final QuestionEmbeddingTextBuilder embeddingTextBuilder;

	public QuestionRetrievalService(
		QuestionRepository questionRepository,
		EmbeddingClient embeddingClient,
		QuestionEmbeddingTextBuilder embeddingTextBuilder
	) {
		this.questionRepository = questionRepository;
		this.embeddingClient = embeddingClient;
		this.embeddingTextBuilder = embeddingTextBuilder;
	}

	public QuestionPayload selectFirstQuestion(String mode, String seniority, List<String> skills, List<String> focusAreas) {
		return selectQuestion(mode, seniority, skills, focusAreas, List.of(),
			() -> questionRepository.findFirstPublishedQuestion(mode, seniority));
	}

	public QuestionPayload selectNextQuestion(String mode, String seniority, List<String> skills, List<String> focusAreas, List<UUID> excludedIds) {
		List<UUID> safeExcluded = excludedIds == null ? List.of() : List.copyOf(excludedIds);
		return selectQuestion(mode, seniority, skills, focusAreas, safeExcluded,
			() -> questionRepository.findNextPublishedQuestion(mode, seniority, safeExcluded));
	}

	private QuestionPayload selectQuestion(
		String mode,
		String seniority,
		List<String> skills,
		List<String> focusAreas,
		List<UUID> excludedIds,
		Supplier<Optional<QuestionPayload>> deterministicFallback
	) {
		QuestionPayload fallback = deterministicFallback.get().orElse(null);

		List<QuestionRetrievalCandidate> candidates = vectorCandidates(mode, seniority, excludedIds, skills, focusAreas);
		if (candidates.isEmpty()) {
			return requireQuestion(fallback, mode);
		}

		List<QuestionDef> recentTopics = recentTopics(excludedIds);
		Set<String> normalizedSkills = normalizeSet(skills);
		Set<String> normalizedFocusAreas = normalizeSet(focusAreas);
		String targetDifficulty = targetDifficultyForSeniority(seniority);

		return candidates.stream()
			.filter(this::isFresh)
			.max(Comparator.comparingDouble(candidate ->
				scoreCandidate(candidate, normalizedSkills, normalizedFocusAreas, recentTopics, seniority, targetDifficulty)
			))
			.map(QuestionRetrievalCandidate::question)
			.orElseGet(() -> requireQuestion(fallback, mode));
	}

	private List<QuestionRetrievalCandidate> vectorCandidates(
		String mode,
		String seniority,
		List<UUID> excludedIds,
		List<String> skills,
		List<String> focusAreas
	) {
		String queryText = buildQueryText(mode, seniority, skills, focusAreas);
		EmbeddingResult queryEmbedding;
		try {
			queryEmbedding = embeddingClient.embed(queryText);
		} catch (RuntimeException ex) {
			return List.of();
		}

		String queryVector = embeddingTextBuilder.toPgVectorLiteral(queryEmbedding.embedding());
		try {
			return questionRepository.findRetrievalCandidates(mode, seniority, excludedIds, queryVector);
		} catch (RuntimeException ex) {
			return List.of();
		}
	}

	private boolean isFresh(QuestionRetrievalCandidate candidate) {
		QuestionDef question = toQuestionDef(candidate.question());
		String currentHash = embeddingTextBuilder.hashCanonicalText(embeddingTextBuilder.buildCanonicalText(question));
		return currentHash.equals(candidate.embeddedTextHash());
	}

	private double scoreCandidate(
		QuestionRetrievalCandidate candidate,
		Set<String> skills,
		Set<String> focusAreas,
		List<QuestionDef> recentTopics,
		String seniority,
		String targetDifficulty
	) {
		Set<String> candidateSignals = candidateSignals(candidate.question());
		double score = 1.0 - candidate.distance();
		score += overlap(candidateSignals, focusAreas) * 2.0;
		score += overlap(candidateSignals, skills) * 1.0;
		score += candidate.question().seniority() != null && candidate.question().seniority().equalsIgnoreCase(normalizeEnum(seniority)) ? 1.0 : -1.5;
		score += targetDifficulty.equalsIgnoreCase(candidate.question().difficulty()) ? 1.5 : -1.5;
		score -= recentTopicPenalty(candidateSignals, recentTopics) * 1.25;
		return score;
	}

	private double recentTopicPenalty(Set<String> candidateSignals, List<QuestionDef> recentTopics) {
		if (recentTopics.isEmpty()) {
			return 0.0;
		}
		Set<String> recentSignals = recentTopics.stream()
			.flatMap(question -> Stream.concat(question.tags().stream(), question.expectedConcepts().stream()))
			.map(this::normalizeToken)
			.collect(Collectors.toSet());
		return overlap(candidateSignals, recentSignals);
	}

	private Set<String> candidateSignals(QuestionPayload question) {
		return Stream.concat(question.tags().stream(), question.expectedConcepts().stream())
			.map(this::normalizeToken)
			.collect(Collectors.toSet());
	}

	private List<QuestionDef> recentTopics(List<UUID> excludedIds) {
		if (excludedIds == null || excludedIds.isEmpty()) {
			return List.of();
		}
		List<QuestionDef> recent = new ArrayList<>();
		for (UUID excludedId : excludedIds) {
			questionRepository.findById(excludedId).ifPresent(question -> recent.add(toQuestionDef(question)));
		}
		return recent;
	}

	private double overlap(Set<String> left, Set<String> right) {
		if (left.isEmpty() || right.isEmpty()) {
			return 0.0;
		}
		Set<String> intersection = new HashSet<>(left);
		intersection.retainAll(right);
		return intersection.size();
	}

	private Set<String> normalizeSet(List<String> values) {
		if (values == null || values.isEmpty()) {
			return Set.of();
		}
		return values.stream()
			.filter(value -> value != null && !value.isBlank())
			.map(this::normalizeToken)
			.collect(Collectors.toSet());
	}

	private String buildQueryText(String mode, String seniority, List<String> skills, List<String> focusAreas) {
		return String.join("\n",
			"Mode: " + normalizeEnum(mode),
			"Seniority: " + normalizeEnum(seniority),
			"Skills: " + String.join(", ", safeList(skills)),
			"Focus Areas: " + String.join(", ", safeList(focusAreas))
		);
	}

	private List<String> safeList(List<String> input) {
		return input == null ? List.of() : input;
	}

	private String normalizeEnum(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	private String normalizeToken(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
	}

	private String targetDifficultyForSeniority(String seniority) {
		String normalized = normalizeEnum(seniority);
		return switch (normalized) {
			case "JUNIOR" -> "EASY";
			case "MID" -> "MEDIUM";
			case "SENIOR", "STAFF" -> "HARD";
			default -> "MEDIUM";
		};
	}

	private QuestionPayload requireQuestion(QuestionPayload question, String mode) {
		if (question == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No published question matched the requested mode");
		}
		return question;
	}

	private QuestionDef toQuestionDef(QuestionPayload question) {
		return new QuestionDef(
			question.title(),
			question.prompt(),
			question.mode(),
			question.difficulty(),
			question.seniority(),
			question.tags(),
			question.expectedConcepts(),
			question.rubric()
		);
	}
}
