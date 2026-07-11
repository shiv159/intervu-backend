package com.intervu.interview;

import com.intervu.ai.LlmAnswerEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.intervu.interview.InterviewDtos.EvaluationDraft;
import static com.intervu.interview.InterviewDtos.QuestionPayload;

@Component
public class AnswerEvaluator {

	private static final Logger log = LoggerFactory.getLogger(AnswerEvaluator.class);

	private final String aiMode;
	private final LlmAnswerEvaluator llmEvaluator;

	public AnswerEvaluator(
		@Value("${intervu.ai.mode:MOCK}") String aiMode,
		LlmAnswerEvaluator llmEvaluator
	) {
		this.aiMode = aiMode;
		this.llmEvaluator = llmEvaluator;
	}

	public EvaluationDraft evaluate(QuestionPayload question, String answer) {
		String normalizedAnswer = answer == null ? "" : answer.trim();
		if ("LIVE".equalsIgnoreCase(aiMode) && llmEvaluator != null) {
			try {
				return llmEvaluator.evaluate(question, normalizedAnswer);
			} catch (Exception ex) {
				log.warn("LLM evaluation failed for question {}: {}", question.id(), ex.getMessage());
				return evaluateFallback(question, normalizedAnswer);
			}
		}
		log.warn("Deterministic mock evaluator in use for question {} in MOCK mode", question.id());
		return evaluateFallback(question, normalizedAnswer);
	}

	public EvaluationDraft evaluateFallback(QuestionPayload question, String normalizedAnswer) {
		List<String> strengths = new ArrayList<>();
		List<String> gaps = new ArrayList<>();
		Map<String, Integer> rubricScores = new LinkedHashMap<>();

		int score = normalizedAnswer.isBlank() ? 18 : 42;
		score += Math.min(24, normalizedAnswer.length() / 12);

		String lowerAnswer = normalizedAnswer.toLowerCase(Locale.ROOT);
		List<String> expectedConcepts = question.expectedConcepts();

		for (String concept : expectedConcepts) {
			if (lowerAnswer.contains(concept.toLowerCase(Locale.ROOT))) {
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

		return new EvaluationDraft(score, rubricScores, List.copyOf(strengths), List.copyOf(gaps), followUpQuestion, "local", "deterministic-fallback", 0L, 0.0, "deterministic-v1", "prompt-v1");
	}

	private boolean containsAny(String text, String... needles) {
		for (String needle : needles) {
			if (text.contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
