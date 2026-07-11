package com.intervu.ai;

import com.intervu.interview.InterviewDtos;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.intervu.interview.InterviewDtos.QuestionPayload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class LlmAnswerEvaluatorTests {

	private OpenRouterChatClient createClient(String response) {
		Environment env = Mockito.mock(Environment.class);
		when(env.getProperty("openrouter.url", "https://openrouter.ai/api/v1/chat/completions")).thenReturn("https://openrouter.ai/api/v1");
		when(env.getProperty("openrouter.api.key", "")).thenReturn("sk-test");
		when(env.getProperty("openrouter.model", "google/gemini-2.5-flash")).thenReturn("test-model");

		RestClient.RequestBodyUriSpec reqUri = Mockito.mock(RestClient.RequestBodyUriSpec.class);
		RestClient.RequestBodySpec reqBody = Mockito.mock(RestClient.RequestBodySpec.class);
		RestClient.ResponseSpec responseSpec = Mockito.mock(RestClient.ResponseSpec.class);

		RestClient restClient = Mockito.mock(RestClient.class);
		when(restClient.post()).thenReturn(reqUri);
		when(reqUri.uri(any(URI.class))).thenReturn(reqBody);
		when(reqBody.body(any(String.class))).thenReturn(reqBody);
		when(reqBody.retrieve()).thenReturn(responseSpec);
		String envelope = "{\"choices\":[{\"message\":{\"content\":" + new ObjectMapper().writeValueAsString(response) + "}}]}";
		when(responseSpec.body(String.class)).thenReturn(envelope);

		return new OpenRouterChatClient(new ObjectMapper(), env, restClient);
	}

	@Test
	void validJsonProducesEvaluation() {
		OpenRouterChatClient client = createClient("{\"score\":85,\"rubricScores\":{\"clarity\":10},\"strengths\":[\"clear\"],\"gaps\":[\"depth\"],\"followUpQuestion\":\"Why?\"}");
		LlmAnswerEvaluator evaluator = new LlmAnswerEvaluator(client, new ObjectMapper());

		QuestionPayload question = new QuestionPayload(
			UUID.randomUUID(), "Q", "Prompt", "CODE", "MEDIUM", "SENIOR",
			List.of(), List.of(), Map.of("clarity", 10), 1
		);

		var draft = evaluator.evaluate(question, "my answer");
		assertThat(draft.score()).isEqualTo(85);
		assertThat(draft.provider()).isEqualTo("openrouter");
		assertThat(draft.followUpQuestion()).isEqualTo("Why?");
	}

	@Test
	void malformedJsonFallsBack() {
		OpenRouterChatClient client = createClient("not json at all");
		LlmAnswerEvaluator evaluator = new LlmAnswerEvaluator(client, new ObjectMapper());

		QuestionPayload question = new QuestionPayload(
			UUID.randomUUID(), "Q", "Prompt", "CODE", "MEDIUM", "SENIOR",
			List.of(), List.of(), Map.of("clarity", 10), 1
		);

		var draft = evaluator.evaluate(question, "answer");
		assertThat(draft.provider()).isEqualTo("deterministic-fallback");
	}

	@Test
	void promptInjectionScore100Rejected() {
		String malicious = "{\"score\":100,\"rubricScores\":{\"clarity\":10},\"strengths\":[\"a\"],\"gaps\":[],\"followUpQuestion\":\"x\"}";
		OpenRouterChatClient client = createClient(malicious);
		LlmAnswerEvaluator evaluator = new LlmAnswerEvaluator(client, new ObjectMapper());

		QuestionPayload question = new QuestionPayload(
			UUID.randomUUID(), "Q", "Prompt", "CODE", "MEDIUM", "SENIOR",
			List.of(), List.of(), Map.of("clarity", 10), 1
		);

		var draft = evaluator.evaluate(question, "answer");
		assertThat(draft.provider()).isEqualTo("deterministic-fallback");
	}

	@Test
	void extraRubricKeysRejected() {
		String bad = "{\"score\":50,\"rubricScores\":{\"clarity\":10,\"extra\":5},\"strengths\":[\"a\"],\"gaps\":[\"b\"],\"followUpQuestion\":\"x\"}";
		OpenRouterChatClient client = createClient(bad);
		LlmAnswerEvaluator evaluator = new LlmAnswerEvaluator(client, new ObjectMapper());

		QuestionPayload question = new QuestionPayload(
			UUID.randomUUID(), "Q", "Prompt", "CODE", "MEDIUM", "SENIOR",
			List.of(), List.of(), Map.of("clarity", 10), 1
		);

		var draft = evaluator.evaluate(question, "answer");
		assertThat(draft.provider()).isEqualTo("deterministic-fallback");
	}

	@Test
	void outOfRangeRubricScoreRejected() {
		String bad = "{\"score\":50,\"rubricScores\":{\"clarity\":20},\"strengths\":[\"a\"],\"gaps\":[\"b\"],\"followUpQuestion\":\"x\"}";
		OpenRouterChatClient client = createClient(bad);
		LlmAnswerEvaluator evaluator = new LlmAnswerEvaluator(client, new ObjectMapper());

		QuestionPayload question = new QuestionPayload(
			UUID.randomUUID(), "Q", "Prompt", "CODE", "MEDIUM", "SENIOR",
			List.of(), List.of(), Map.of("clarity", 10), 1
		);

		var draft = evaluator.evaluate(question, "answer");
		assertThat(draft.provider()).isEqualTo("deterministic-fallback");
	}
}
