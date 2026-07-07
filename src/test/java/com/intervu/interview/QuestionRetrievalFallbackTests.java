package com.intervu.interview;

import com.intervu.questionbank.embedding.EmbeddingClient;
import com.intervu.questionbank.embedding.EmbeddingResult;
import com.intervu.questionbank.embedding.QuestionEmbeddingTextBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.intervu.interview.InterviewDtos.QuestionPayload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionRetrievalFallbackTests {

	@Mock
	QuestionRepository questionRepository;

	@Mock
	EmbeddingClient embeddingClient;

	private final QuestionEmbeddingTextBuilder embeddingTextBuilder = new QuestionEmbeddingTextBuilder();

	@Test
	void fallsBackWhenVectorTableQueryFails() {
		QuestionPayload fallback = questionPayload(UUID.randomUUID(), 1, "EASY", "java");
		EmbeddingResult embeddingResult = new EmbeddingResult("openai/text-embedding-3-small", List.of(0.1, 0.2, 0.3));

		when(questionRepository.findFirstPublishedQuestion("CODE", "SENIOR"))
			.thenReturn(Optional.of(fallback));
		when(embeddingClient.embed(anyString())).thenReturn(embeddingResult);
		when(questionRepository.findRetrievalCandidates(eq("CODE"), eq("SENIOR"), anyList(), anyString()))
			.thenThrow(new RuntimeException("relation \"question_embeddings\" does not exist"));

		QuestionRetrievalService service = new QuestionRetrievalService(questionRepository, embeddingClient, embeddingTextBuilder);

		QuestionPayload result = service.selectFirstQuestion("CODE", "SENIOR", List.of("java"), List.of("apis"));

		assertThat(result).isEqualTo(fallback);
	}

	private QuestionPayload questionPayload(UUID id, int version, String difficulty, String... tags) {
		return new QuestionPayload(
			id,
			"Question " + id.toString().substring(0, 8),
			"Prompt " + id.toString().substring(0, 8),
			"CODE",
			difficulty,
			"SENIOR",
			List.of(tags),
			List.of("caching", "scaling"),
			Map.of("architecture", 20),
			version
		);
	}
}