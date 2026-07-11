package com.intervu.ai;

import com.intervu.interview.InterviewDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intervu.interview.InterviewDtos.EvaluationDraft;
import static com.intervu.interview.InterviewDtos.QuestionPayload;

@Component
public class LlmAnswerEvaluator {

	private static final Logger log = LoggerFactory.getLogger(LlmAnswerEvaluator.class);
	private static final String EVALUATOR_VERSION = "llm-v1";
	private static final String PROMPT_VERSION = "prompt-v1";
	private static final int MAX_ARRAY_ITEMS = 10;

	private final OpenRouterChatClient chatClient;
	private final ObjectMapper objectMapper;

	public LlmAnswerEvaluator(OpenRouterChatClient chatClient, ObjectMapper objectMapper) {
		this.chatClient = chatClient;
		this.objectMapper = objectMapper;
	}

	public EvaluationDraft evaluate(QuestionPayload question, String answer) {
		String systemPrompt = buildSystemPrompt(question);
		String userPrompt = buildUserPrompt(question, answer);

		LlmEvaluationResult result = attemptEvaluation(systemPrompt, userPrompt, question);
		if (result != null) {
			return new EvaluationDraft(
				result.score(),
				result.rubricScores(),
				result.strengths(),
				result.gaps(),
				result.followUpQuestion(),
				result.model(),
				"openrouter",
				result.latencyMs(),
				result.cost(),
				EVALUATOR_VERSION,
				PROMPT_VERSION
			);
		}

		log.warn("LLM evaluation failed after retry for question {}, falling back to deterministic evaluator", question.id());
		return evaluateFallback(question, answer);
	}

	private LlmEvaluationResult attemptEvaluation(String systemPrompt, String userPrompt, QuestionPayload question) {
		LlmEvaluationResult result = tryParseEvaluation(chatClient.chat(systemPrompt, userPrompt), question);
		if (result != null) {
			return result;
		}

		String repairPrompt = systemPrompt + "\n\nYour previous response did not match the required JSON schema. Respond ONLY with valid JSON matching this exact shape: {\"score\":0,\"rubricScores\":{},\"strengths\":[],\"gaps\":[],\"followUpQuestion\":\"\"}";
		return tryParseEvaluation(chatClient.chat(repairPrompt, userPrompt), question);
	}

	private LlmEvaluationResult tryParseEvaluation(String raw, QuestionPayload question) {
		if (raw == null || raw.isBlank()) {
			return null;
		}

		String cleaned = raw.trim();
		int firstBrace = cleaned.indexOf('{');
		int lastBrace = cleaned.lastIndexOf('}');
		if (firstBrace < 0 || lastBrace < 0 || lastBrace <= firstBrace) {
			return null;
		}
		String json = cleaned.substring(firstBrace, lastBrace + 1);

		try {
			EvaluationPayload payload = objectMapper.readValue(json, EvaluationPayload.class);
			if (!isValid(payload, question)) {
				return null;
			}
			return new LlmEvaluationResult(
				payload.score(),
				payload.rubricScores(),
				payload.strengths(),
				payload.gaps(),
				payload.followUpQuestion(),
				"openrouter/" + chatClient.model(),
				System.currentTimeMillis(),
				0.0
			);
		} catch (Exception ex) {
			log.warn("Failed to parse LLM evaluation JSON: {}", ex.getMessage());
			return null;
		}
	}

	private boolean isValid(EvaluationPayload payload, QuestionPayload question) {
		if (payload.score() == null || payload.score() < 0 || payload.score() > 100) {
			return false;
		}
		if (payload.score() == 100) {
			return false;
		}
		if (payload.rubricScores() == null) {
			return false;
		}
		Set<String> allowedKeys = question.rubric().keySet();
		for (String key : payload.rubricScores().keySet()) {
			if (!allowedKeys.contains(key)) {
				return false;
			}
			Integer value = payload.rubricScores().get(key);
			if (value == null || value < 0) {
				return false;
			}
			Integer max = question.rubric().get(key);
			if (max != null && value > max) {
				return false;
			}
		}
		if (payload.strengths() != null && payload.strengths().size() > MAX_ARRAY_ITEMS) {
			return false;
		}
		if (payload.gaps() != null && payload.gaps().size() > MAX_ARRAY_ITEMS) {
			return false;
		}
		if (payload.followUpQuestion() == null || payload.followUpQuestion().isBlank()) {
			return false;
		}
		return true;
	}

	private String buildSystemPrompt(QuestionPayload question) {
		return """
			You are an expert technical interviewer. Evaluate the candidate's answer against the provided rubric.
			Respond ONLY with a JSON object matching this schema:
			{
			  "score": 0,
			  "rubricScores": {},
			  "strengths": [],
			  "gaps": [],
			  "followUpQuestion": ""
			}
			Rules:
			- score is an integer 0-100
			- rubricScores keys must be a subset of: %s
			- each rubric value must be >=0 and <= the rubric max
			- strengths and gaps arrays must have at most 10 items
			- followUpQuestion must be a non-empty string
			""".formatted(String.join(", ", question.rubric().keySet()));
	}

	private String buildUserPrompt(QuestionPayload question, String answer) {
		StringBuilder sb = new StringBuilder();
		sb.append("Question: ").append(question.title()).append("\n");
		sb.append("Mode: ").append(question.mode()).append("\n");
		sb.append("Expected concepts: ").append(String.join(", ", question.expectedConcepts())).append("\n");
		sb.append("Rubric: ").append(question.rubric()).append("\n\n");
		sb.append("[CANDIDATE_ANSWER]\n");
		sb.append(answer == null ? "" : answer);
		sb.append("\n[/CANDIDATE_ANSWER]");
		return sb.toString();
	}

	private EvaluationDraft evaluateFallback(QuestionPayload question, String normalizedAnswer) {
		List<String> strengths = new java.util.ArrayList<>();
		List<String> gaps = new java.util.ArrayList<>();
		Map<String, Integer> rubricScores = new java.util.LinkedHashMap<>();

		int score = normalizedAnswer.isBlank() ? 18 : 42;
		score += Math.min(24, normalizedAnswer.length() / 12);

		String lowerAnswer = normalizedAnswer.toLowerCase(java.util.Locale.ROOT);
		List<String> expectedConcepts = question.expectedConcepts();

		for (String concept : expectedConcepts) {
			if (lowerAnswer.contains(concept.toLowerCase(java.util.Locale.ROOT))) {
				score += 4;
				strengths.add("Mentions " + concept);
			}
		}

		if (lowerAnswer.contains("trade") || lowerAnswer.contains("trade-off")) {
			score += 6;
			strengths.add("Discusses trade-offs");
		} else {
			gaps.add("Could explain the trade-offs more clearly.");
		}

		switch (question.mode()) {
			case "CODE" -> {
				if (containsAny(lowerAnswer, "map", "hash", "set", "loop", "complexity")) {
					score += 10;
					strengths.add("Touches on implementation detail");
				} else {
					gaps.add("Could describe the algorithm more concretely.");
				}
				if (!containsAny(lowerAnswer, "edge case", "null", "duplicate", "boundary")) {
					gaps.add("Edge cases are worth calling out.");
				}
			}
			case "SYSTEM_DESIGN" -> {
				if (containsAny(lowerAnswer, "api", "cache", "service", "database", "scal", "reliab", "failure")) {
					score += 10;
					strengths.add("Covers system-level components");
				} else {
					gaps.add("Could describe services and data flow in more detail.");
				}
				if (!containsAny(lowerAnswer, "scale", "throughput", "latency", "bottleneck")) {
					gaps.add("Scaling and bottlenecks need more depth.");
				}
			}
			default -> {
				if (containsAny(lowerAnswer, "specific", "result", "owned", "learned", "improved", "led")) {
					score += 10;
					strengths.add("Uses concrete outcome language");
				} else {
					gaps.add("Needs a more specific example.");
				}
				if (!containsAny(lowerAnswer, "impact", "result", "change", "learned")) {
					gaps.add("Could close with the impact or lesson learned.");
				}
			}
		}

		if (normalizedAnswer.isBlank()) {
			gaps.add(0, "Answer was empty.");
		} else if (normalizedAnswer.length() < 40) {
			gaps.add("The answer is very short and may need more detail.");
		}

		score = Math.max(0, Math.min(100, score));

		for (Map.Entry<String, Integer> entry : question.rubric().entrySet()) {
			int categoryScore = Math.min(entry.getValue(), Math.round(entry.getValue() * (score / 100.0f)));
			rubricScores.put(entry.getKey(), categoryScore);
		}

		if (strengths.isEmpty() && !normalizedAnswer.isBlank()) {
			strengths.add("Provides a direct response");
		}
		if (gaps.isEmpty()) {
			gaps.add("Try adding one more level of detail.");
		}

		String followUpQuestion = score >= 80
			? "Can you talk through the main trade-offs one more time?"
			: switch (question.mode()) {
				case "CODE" -> "How would you handle edge cases and duplicate inputs?";
				case "SYSTEM_DESIGN" -> "What happens when the hot path becomes the bottleneck?";
				default -> "Can you make the example more specific and measurable?";
			};

		return new EvaluationDraft(score, rubricScores, List.copyOf(strengths), List.copyOf(gaps), followUpQuestion, "local", "deterministic-fallback", 0L, 0.0, EVALUATOR_VERSION, PROMPT_VERSION);
	}

	private boolean containsAny(String text, String... needles) {
		for (String needle : needles) {
			if (text.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private record LlmEvaluationResult(
		int score,
		Map<String, Integer> rubricScores,
		List<String> strengths,
		List<String> gaps,
		String followUpQuestion,
		String model,
		long latencyMs,
		double cost
	) {
	}

	private record EvaluationPayload(
		Integer score,
		Map<String, Integer> rubricScores,
		List<String> strengths,
		List<String> gaps,
		String followUpQuestion
	) {
	}
}
