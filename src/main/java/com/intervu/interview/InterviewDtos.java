package com.intervu.interview;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InterviewDtos {

	private InterviewDtos() {
	}

	public record CreateInterviewRequest(
		@NotBlank String targetRole,
		@NotBlank String seniority,
		@NotBlank String mode,
		List<String> skills,
		List<String> focusAreas
	) {
	}

	public record AnswerSubmissionRequest(String answer) {
	}

	public record QuestionPayload(
		UUID id,
		String title,
		String prompt,
		String mode,
		String difficulty,
		String seniority,
		List<String> tags,
		List<String> expectedConcepts,
		Map<String, Integer> rubric,
		int version
	) {
	}

	public record EvaluationSummary(
		UUID evaluationId,
		Integer totalScore,
		Map<String, Integer> rubricScores,
		List<String> strengths,
		List<String> gaps,
		String followUpQuestion
	) {
	}

	public record InterviewSessionResponse(
		UUID sessionId,
		String ownerId,
		String targetRole,
		String state,
		String mode,
		String seniority,
		String difficulty,
		long stateVersion,
		List<String> skills,
		List<String> focusAreas,
		Integer currentQuestionVersion,
		QuestionPayload currentQuestion,
		EvaluationSummary lastEvaluation
	) {
	}

	public record AnswerSubmissionResponse(
		UUID interactionId,
		InterviewSessionResponse session,
		EvaluationSummary evaluation
	) {
	}

	public record FeedbackResponse(
		UUID sessionId,
		String summary,
		Integer overallScore,
		List<String> strengths,
		List<String> gaps,
		String followUpQuestion
	) {
	}

	public record SessionEventResponse(
		long eventVersion,
		String eventType,
		Map<String, Object> payload
	) {
	}

	public record EvaluationDraft(
		int score,
		Map<String, Integer> rubricScores,
		List<String> strengths,
		List<String> gaps,
		String followUpQuestion,
		String model,
		String provider,
		Long latencyMs,
		Double cost
	) {
	}

	public record SessionRow(
		UUID id,
		String ownerId,
		String targetRole,
		String state,
		String mode,
		String seniority,
		String difficulty,
		List<String> skills,
		List<String> focusAreas,
		UUID currentQuestionId,
		Integer currentQuestionVersion,
		long stateVersion
	) {
	}

	public record InteractionRow(
		UUID id,
		UUID sessionId,
		UUID questionId,
		Integer questionVersion,
		String idempotencyKey,
		String payload
	) {
	}

	public record EvaluationRow(
		UUID id,
		UUID sessionId,
		UUID interactionId,
		int score,
		Map<String, Integer> rubricScores,
		List<String> strengths,
		List<String> gaps,
		String followUpQuestion,
		String model,
		String provider,
		Long latencyMs,
		Double cost
	) {
	}

	public record SessionEventRow(
		long eventVersion,
		String eventType,
		Map<String, Object> payload
	) {
	}

}
