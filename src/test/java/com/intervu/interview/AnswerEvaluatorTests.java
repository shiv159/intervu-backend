package com.intervu.interview;

import com.intervu.ai.LlmAnswerEvaluator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.intervu.interview.InterviewDtos.EvaluationDraft;
import static com.intervu.interview.InterviewDtos.QuestionPayload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerEvaluatorTests {

	private QuestionPayload question() {
		return new QuestionPayload(
			UUID.randomUUID(), "Q", "Prompt", "CODE", "MEDIUM", "SENIOR",
			List.of(), List.of(), Map.of("clarity", 10), 1
		);
	}

	private EvaluationDraft llmDraft() {
		return new EvaluationDraft(
			85, Map.of("clarity", 10), List.of("clear"), List.of("depth"),
			"Why?", "openrouter/gemini", "openrouter", 120L, 0.01, "llm-v1", "prompt-v1");
	}

	@Test
	void liveModeDelegatesToLlmEvaluator() {
		LlmAnswerEvaluator llmEvaluator = mock(LlmAnswerEvaluator.class);
		when(llmEvaluator.evaluate(any(), any())).thenReturn(llmDraft());

		AnswerEvaluator evaluator = new AnswerEvaluator("LIVE", llmEvaluator);
		QuestionPayload question = question();

		EvaluationDraft draft = evaluator.evaluate(question, "a strong answer");

		verify(llmEvaluator).evaluate(question, "a strong answer");
		assertThat(draft.provider()).isEqualTo("openrouter");
		assertThat(draft.score()).isEqualTo(85);
	}

	@Test
	void mockModeNeverCallsLlmEvaluator() {
		LlmAnswerEvaluator llmEvaluator = mock(LlmAnswerEvaluator.class);

		AnswerEvaluator evaluator = new AnswerEvaluator("MOCK", llmEvaluator);
		QuestionPayload question = question();

		EvaluationDraft draft = evaluator.evaluate(question, "an answer");

		verify(llmEvaluator, never()).evaluate(any(), any());
		assertThat(draft.provider()).isEqualTo("deterministic-fallback");
	}

	@Test
	void liveModeFallsBackWhenLlmThrows() {
		LlmAnswerEvaluator llmEvaluator = mock(LlmAnswerEvaluator.class);
		when(llmEvaluator.evaluate(any(), any())).thenThrow(new RuntimeException("boom"));

		AnswerEvaluator evaluator = new AnswerEvaluator("LIVE", llmEvaluator);
		QuestionPayload question = question();

		EvaluationDraft draft = evaluator.evaluate(question, "an answer");

		assertThat(draft.provider()).isEqualTo("deterministic-fallback");
	}
}
