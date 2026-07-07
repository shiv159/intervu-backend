package com.intervu.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardDtos {

	private DashboardDtos() {
	}

	public record DashboardSessionSummary(
		UUID sessionId,
		String targetRole,
		String mode,
		String seniority,
		String state,
		Integer overallScore,
		String summary,
		long stateVersion,
		Instant createdAt,
		Instant completedAt
	) {
	}

	public record SessionFeedbackResponse(
		UUID sessionId,
		String targetRole,
		String mode,
		String seniority,
		String overallReadiness,
		Integer overallScore,
		List<RoundFeedback> rounds,
		Map<String, TopicMastery> topicMastery,
		List<String> strengths,
		List<String> areasForGrowth,
		List<String> recommendedPractice,
		Instant createdAt,
		Instant completedAt
	) {
	}

	public record RoundFeedback(
		UUID questionId,
		String questionTitle,
		Integer score,
		Map<String, Integer> rubricScores,
		List<String> strengths,
		List<String> gaps,
		String mode
	) {
	}

	public record TopicMastery(
		String topic,
		Integer averageScore,
		Integer questionCount,
		String band
	) {
	}

}