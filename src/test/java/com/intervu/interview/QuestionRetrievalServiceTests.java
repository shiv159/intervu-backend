package com.intervu.interview;

import com.intervu.questionbank.QuestionDef;
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
class QuestionRetrievalServiceTests {

	@Mock
	QuestionRepository questionRepository;

	@Mock
	EmbeddingClient embeddingClient;

	private final QuestionEmbeddingTextBuilder embeddingTextBuilder = new QuestionEmbeddingTextBuilder();

	@Test
	void fallsBackToDeterministicQuestionWhenEmbeddingsUnavailable() {
		QuestionPayload fallback = questionPayload(UUID.randomUUID(), 1, "EASY", "java");
		when(questionRepository.findFirstPublishedQuestion("CODE", "SENIOR"))
			.thenReturn(Optional.of(fallback));
		when(embeddingClient.embed(anyString()))
			.thenThrow(new IllegalStateException("embedding unavailable"));

		QuestionRetrievalService service = new QuestionRetrievalService(questionRepository, embeddingClient, embeddingTextBuilder);

		QuestionPayload result = service.selectFirstQuestion("CODE", "SENIOR", List.of("java"), List.of("apis"));

		assertThat(result).isEqualTo(fallback);
	}

	@Test
	void prefersClosestVectorCandidate() {
		QuestionPayload close = questionPayload(UUID.randomUUID(), 2, "MEDIUM", "spring");
		QuestionPayload far = questionPayload(UUID.randomUUID(), 2, "MEDIUM", "graphql");
		EmbeddingResult embeddingResult = new EmbeddingResult("openai/text-embedding-3-small", List.of(0.1, 0.2, 0.3));
		when(questionRepository.findFirstPublishedQuestion("SYSTEM_DESIGN", "SENIOR"))
			.thenReturn(Optional.of(far));
		when(embeddingClient.embed(anyString())).thenReturn(embeddingResult);
		when(questionRepository.findRetrievalCandidates(eq("SYSTEM_DESIGN"), eq("SENIOR"), anyList(), anyString()))
			.thenReturn(List.of(
				new QuestionRetrievalCandidate(close, 0.05, currentHash(close)),
				new QuestionRetrievalCandidate(far, 0.30, currentHash(far))
			));

		QuestionRetrievalService service = new QuestionRetrievalService(questionRepository, embeddingClient, embeddingTextBuilder);

		QuestionPayload result = service.selectFirstQuestion("SYSTEM_DESIGN", "SENIOR", List.of("kafka"), List.of("caching"));

		assertThat(result).isEqualTo(close);
	}

	@Test
	void rerankingBoostsFocusAreasAndPenalizesWrongDifficultyAndRecentTopics() {
		QuestionPayload weakDistance = questionPayload(UUID.randomUUID(), 3, "HARD", "redis");
		QuestionPayload strongFocus = questionPayload(UUID.randomUUID(), 2, "MEDIUM", "kafka", "caching");
		QuestionPayload recentTopic = questionPayload(UUID.randomUUID(), 2, "MEDIUM", "incident-response");
		QuestionPayload recentSignalCandidate = questionPayload(UUID.randomUUID(), 2, "MEDIUM", "incident-response");
		EmbeddingResult embeddingResult = new EmbeddingResult("openai/text-embedding-3-small", List.of(0.2, 0.4, 0.6));

		when(questionRepository.findNextPublishedQuestion("SYSTEM_DESIGN", "MID", List.of(recentTopic.id())))
			.thenReturn(Optional.of(weakDistance));
		when(embeddingClient.embed(anyString())).thenReturn(embeddingResult);
		when(questionRepository.findRetrievalCandidates(eq("SYSTEM_DESIGN"), eq("MID"), anyList(), anyString()))
			.thenReturn(List.of(
				new QuestionRetrievalCandidate(weakDistance, 0.03, currentHash(weakDistance)),
				new QuestionRetrievalCandidate(strongFocus, 0.20, currentHash(strongFocus)),
				new QuestionRetrievalCandidate(recentSignalCandidate, 0.15, currentHash(recentSignalCandidate))
			));
		when(questionRepository.findById(recentTopic.id()))
			.thenReturn(Optional.of(recentTopic));

		QuestionRetrievalService service = new QuestionRetrievalService(questionRepository, embeddingClient, embeddingTextBuilder);

		QuestionPayload result = service.selectNextQuestion(
			"SYSTEM_DESIGN",
			"MID",
			List.of("kafka", "spring boot"),
			List.of("caching"),
			List.of(recentTopic.id())
		);

		assertThat(result).isEqualTo(strongFocus);
	}

	private String currentHash(QuestionPayload question) {
		return embeddingTextBuilder.hashCanonicalText(embeddingTextBuilder.buildCanonicalText(toDef(question)));
	}

	private QuestionDef toDef(QuestionPayload question) {
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

	private QuestionPayload questionPayload(UUID id, int version, String difficulty, String... tags) {
		return new QuestionPayload(
			id,
			"Question " + id.toString().substring(0, 8),
			"Prompt " + id.toString().substring(0, 8),
			"SYSTEM_DESIGN",
			difficulty,
			"SENIOR",
			List.of(tags),
			List.of("caching", "scaling"),
			Map.of("architecture", 20),
			version
		);
	}
}
