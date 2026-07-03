package com.intervu.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	@Value("${openrouter.url}")
	private String openrouterUrl;

	@Value("${openrouter.api.key}")
	private String openrouterApiKey;

	@Value("${openrouter.model}")
	private String openrouterModel;

	public AnswerEvaluator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.restClient = RestClient.create();
	}

	public EvaluationDraft evaluate(QuestionPayload question, String answer) {
		String normalizedAnswer = answer == null ? "" : answer.trim();

		// If no key is provided, or we fail, fallback to local deterministic evaluator
		if (openrouterApiKey == null || openrouterApiKey.isBlank() || "mock_key_for_build".equals(openrouterApiKey)) {
			log.warn("No valid OpenRouter API key provided. Using fallback deterministic evaluator.");
			return evaluateFallback(question, normalizedAnswer);
		}

		String prompt = buildPrompt(question, normalizedAnswer);

		Map<String, Object> requestBody = Map.of(
			"model", openrouterModel,
			"response_format", Map.of("type", "json_object"),
			"messages", List.of(
				Map.of("role", "system", "content", "You are an expert technical interviewer evaluating a candidate's answer. Output strictly in JSON format."),
				Map.of("role", "user", "content", prompt)
			)
		);

		try {
			String responseString = restClient.post()
				.uri(openrouterUrl)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + openrouterApiKey)
				.header("HTTP-Referer", "http://localhost:4200")
				.header("X-Title", "Intervu")
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody)
				.retrieve()
				.body(String.class);

			return parseLLMResponse(responseString, question);
		} catch (Exception e) {
			log.error("Failed to evaluate using OpenRouter LLM. Falling back to deterministic evaluator.", e);
			return evaluateFallback(question, normalizedAnswer);
		}
	}

	private String buildPrompt(QuestionPayload q, String answer) {
		StringBuilder sb = new StringBuilder();
		sb.append("Evaluate this candidate's interview answer based on the following criteria.\n\n");
		sb.append("### QUESTION CONTEXT\n");
		sb.append("- Role: ").append(q.seniority()).append(" Engineer\n");
		sb.append("- Mode: ").append(q.mode()).append("\n");
		sb.append("- Difficulty: ").append(q.difficulty()).append("\n");
		sb.append("- Prompt: ").append(q.prompt()).append("\n");
		
		sb.append("\n### EXPECTED CONCEPTS\n");
		q.expectedConcepts().forEach(c -> sb.append("- ").append(c).append("\n"));
		
		sb.append("\n### RUBRIC (Category -> Max Points)\n");
		q.rubric().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
		
		sb.append("\n### CANDIDATE'S ANSWER\n");
		sb.append(answer).append("\n\n");
		
		sb.append("### REQUIRED JSON OUTPUT STRUCTURE\n");
		sb.append("You must respond with a JSON object exactly matching this schema:\n");
		sb.append("{\n");
		sb.append("  \"score\": <integer 0-100>,\n");
		sb.append("  \"rubricScores\": { \"<category_name_exactly_as_above>\": <integer> },\n");
		sb.append("  \"strengths\": [ \"<string>\", \"<string>\" ],\n");
		sb.append("  \"gaps\": [ \"<string>\", \"<string>\" ],\n");
		sb.append("  \"followUpQuestion\": \"<string>\"\n");
		sb.append("}\n");

		return sb.toString();
	}

	private EvaluationDraft parseLLMResponse(String openRouterResponse, QuestionPayload q) {
		try {
			var root = objectMapper.readTree(openRouterResponse);
			String content = root.path("choices").path(0).path("message").path("content").asText();
			
			// Some models wrap JSON in markdown block
			if (content.startsWith("```json")) {
				content = content.replaceFirst("```json", "");
				if (content.endsWith("```")) {
					content = content.substring(0, content.length() - 3);
				}
			}

			EvaluationDraft draft = objectMapper.readValue(content, EvaluationDraft.class);
			return validateAndSanitize(draft, q);
		} catch (Exception e) {
			log.error("Failed to parse OpenRouter response: {}", openRouterResponse, e);
			throw new RuntimeException("Invalid JSON from LLM", e);
		}
	}

	private EvaluationDraft validateAndSanitize(EvaluationDraft draft, QuestionPayload q) {
		// Ensure all rubric keys exist, fill missing with 0
		Map<String, Integer> sanitizedRubric = new LinkedHashMap<>();
		q.rubric().forEach((k, maxVal) -> {
			Integer actualVal = draft.rubricScores() != null ? draft.rubricScores().getOrDefault(k, 0) : 0;
			sanitizedRubric.put(k, Math.min(actualVal, maxVal)); // Cap at max
		});

		int safeScore = Math.max(0, Math.min(100, draft.score()));
		List<String> safeStrengths = draft.strengths() != null ? draft.strengths() : List.of();
		List<String> safeGaps = draft.gaps() != null ? draft.gaps() : List.of();
		String safeFollowUp = draft.followUpQuestion() != null ? draft.followUpQuestion() : "What else could be improved?";

		return new EvaluationDraft(safeScore, sanitizedRubric, safeStrengths, safeGaps, safeFollowUp);
	}

	// -------------------------------------------------------------------------
	// FALLBACK DETERMINISTIC EVALUATOR (Original Code)
	// -------------------------------------------------------------------------

	private EvaluationDraft evaluateFallback(QuestionPayload question, String normalizedAnswer) {
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

		return new EvaluationDraft(score, rubricScores, List.copyOf(strengths), List.copyOf(gaps), followUpQuestion);
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
