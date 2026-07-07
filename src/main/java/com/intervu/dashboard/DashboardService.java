package com.intervu.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.intervu.dashboard.DashboardDtos.DashboardSessionSummary;
import static com.intervu.dashboard.DashboardDtos.RoundFeedback;
import static com.intervu.dashboard.DashboardDtos.SessionFeedbackResponse;
import static com.intervu.dashboard.DashboardDtos.TopicMastery;

@Service
public class DashboardService {

	private final DashboardRepository repository;
	private final ObjectMapper objectMapper;

	public DashboardService(DashboardRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	public List<DashboardSessionSummary> listSessions(String ownerId, int page, int size) {
		return repository.findSessionsByOwner(ownerId, page, size);
	}

	public SessionFeedbackResponse getSessionFeedback(UUID sessionId, String ownerId) {
		List<RoundFeedback> rounds = repository.findEvaluationsBySessionId(sessionId);
		if (rounds.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No evaluations found for this session");
		}

		// Aggregate scores
		Integer overallScore = computeOverallScore(rounds);

		// Topic mastery aggregation
		Map<String, TopicMastery> topicMastery = computeTopicMastery(rounds);

		// Strengths and gaps (deduplicated)
		List<String> strengths = deduplicateFlatten(rounds, RoundFeedback::strengths);
		List<String> areasForGrowth = deduplicateFlatten(rounds, RoundFeedback::gaps);

		// Recommended practice from weak topics
		List<String> recommendedPractice = deriveRecommendedPractice(topicMastery);

		String overallReadiness = mapScoreToReadiness(overallScore != null ? overallScore : 0);

		return new SessionFeedbackResponse(
			sessionId,
			null, // targetRole populated below
			null, // mode
			null, // seniority
			overallReadiness,
			overallScore,
			rounds,
			topicMastery,
			strengths,
			areasForGrowth,
			recommendedPractice,
			repository.findSessionCreatedAt(sessionId).orElse(null),
			repository.findSessionCompletedAt(sessionId).orElse(null)
		);
	}

	public SessionFeedbackResponse getSessionFeedbackEnriched(UUID sessionId, String ownerId) {
		// First verify the session exists and user has access
		List<RoundFeedback> rounds = repository.findEvaluationsBySessionId(sessionId);
		if (rounds.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No evaluations found for this session");
		}

		Integer overallScore = computeOverallScore(rounds);
		Map<String, TopicMastery> topicMastery = computeTopicMastery(rounds);
		List<String> strengths = deduplicateFlatten(rounds, RoundFeedback::strengths);
		List<String> areasForGrowth = deduplicateFlatten(rounds, RoundFeedback::gaps);
		List<String> recommendedPractice = deriveRecommendedPractice(topicMastery);
		String overallReadiness = mapScoreToReadiness(overallScore != null ? overallScore : 0);

		return new SessionFeedbackResponse(
			sessionId,
			null,
			null,
			null,
			overallReadiness,
			overallScore,
			rounds,
			topicMastery,
			strengths,
			areasForGrowth,
			recommendedPractice,
			repository.findSessionCreatedAt(sessionId).orElse(null),
			repository.findSessionCompletedAt(sessionId).orElse(null)
		);
	}

	public void deleteSession(UUID sessionId, String ownerId) {
		repository.deleteSessionData(sessionId, ownerId);
	}

	public void generateAnalyticsSnapshot(UUID sessionId) {
		List<RoundFeedback> rounds = repository.findEvaluationsBySessionId(sessionId);
		if (rounds.isEmpty()) return;

		Integer overallScore = computeOverallScore(rounds);
		Map<String, TopicMastery> topicMastery = computeTopicMastery(rounds);

		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("overallScore", overallScore);
		metrics.put("topicMastery", topicMastery);
		metrics.put("roundCount", rounds.size());

		try {
			String metricsJson = objectMapper.writeValueAsString(metrics);
			repository.insertAnalyticsSnapshot(UUID.randomUUID(), sessionId, metricsJson);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate analytics snapshot");
		}
	}

	private Integer computeOverallScore(List<RoundFeedback> rounds) {
		if (rounds.isEmpty()) return null;
		double avg = rounds.stream()
			.mapToInt(RoundFeedback::score)
			.average()
			.orElse(0.0);
		return (int) Math.round(avg);
	}

	private Map<String, TopicMastery> computeTopicMastery(List<RoundFeedback> rounds) {
		// Group rubric scores by rubric category
		Map<String, List<Integer>> categoryScores = new LinkedHashMap<>();

		for (RoundFeedback round : rounds) {
			if (round.rubricScores() != null) {
				for (Map.Entry<String, Integer> entry : round.rubricScores().entrySet()) {
					categoryScores.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
				}
			}
		}

		Map<String, TopicMastery> mastery = new LinkedHashMap<>();
		for (Map.Entry<String, List<Integer>> entry : categoryScores.entrySet()) {
			String topic = entry.getKey();
			List<Integer> scores = entry.getValue();
			int avg = (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0.0));
			String band = mapScoreToReadiness(avg);
			mastery.put(topic, new TopicMastery(topic, avg, scores.size(), band));
		}
		return mastery;
	}

	private List<String> deriveRecommendedPractice(Map<String, TopicMastery> topicMastery) {
		List<String> recommendations = new ArrayList<>();
		for (TopicMastery mastery : topicMastery.values()) {
			if ("Needs Work".equals(mastery.band()) || "Mixed".equals(mastery.band())) {
				recommendations.add("Practice " + mastery.topic() + " (current: " + mastery.averageScore() + "/100, " + mastery.questionCount() + " question" + (mastery.questionCount() > 1 ? "s" : "") + ")");
			}
		}
		return recommendations;
	}

	private String mapScoreToReadiness(int score) {
		if (score >= 80) return "Strong";
		if (score >= 60) return "Solid";
		if (score >= 40) return "Mixed";
		return "Needs Work";
	}

	private <T> List<String> deduplicateFlatten(List<RoundFeedback> rounds, java.util.function.Function<RoundFeedback, List<String>> extractor) {
		Set<String> deduped = new LinkedHashSet<>();
		for (RoundFeedback round : rounds) {
			List<String> items = extractor.apply(round);
			if (items != null) {
				deduped.addAll(items);
			}
		}
		return new ArrayList<>(deduped);
	}
}