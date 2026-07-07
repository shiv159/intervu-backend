package com.intervu.questionbank;

import com.intervu.questionbank.embedding.QuestionEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionPublicationServiceTests {

	@Mock
	QuestionAdminRepository questionAdminRepository;

	@Mock
	QuestionEmbeddingService questionEmbeddingService;

	@Test
	void publishQuestionBuildsEmbeddingBeforeActivation() {
		UUID questionId = UUID.randomUUID();
		QuestionDef question = questionDef();
		when(questionAdminRepository.publishQuestion(questionId)).thenReturn(1);
		when(questionAdminRepository.findPublishedQuestionDefById(questionId)).thenReturn(Optional.of(question));
		when(questionAdminRepository.activateQuestion(questionId)).thenReturn(1);

		QuestionPublicationService service = new QuestionPublicationService(questionAdminRepository, questionEmbeddingService);

		service.publishQuestion(questionId);

		InOrder order = inOrder(questionAdminRepository, questionEmbeddingService);
		order.verify(questionAdminRepository).publishQuestion(questionId);
		order.verify(questionAdminRepository).findPublishedQuestionDefById(questionId);
		order.verify(questionEmbeddingService).buildAndStoreEmbedding(questionId, question);
		order.verify(questionAdminRepository).activateQuestion(questionId);
	}

	@Test
	void publishQuestionLeavesQuestionInactiveWhenEmbeddingFails() {
		UUID questionId = UUID.randomUUID();
		QuestionDef question = questionDef();
		when(questionAdminRepository.publishQuestion(questionId)).thenReturn(1);
		when(questionAdminRepository.findPublishedQuestionDefById(questionId)).thenReturn(Optional.of(question));
		doThrow(new IllegalStateException("embedding failed"))
			.when(questionEmbeddingService)
			.buildAndStoreEmbedding(questionId, question);

		QuestionPublicationService service = new QuestionPublicationService(questionAdminRepository, questionEmbeddingService);

		assertThatThrownBy(() -> service.publishQuestion(questionId))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Question embedding build unavailable");

		verify(questionAdminRepository).publishQuestion(questionId);
		verify(questionAdminRepository).findPublishedQuestionDefById(questionId);
		verify(questionEmbeddingService).buildAndStoreEmbedding(questionId, question);
		verify(questionAdminRepository, org.mockito.Mockito.never()).activateQuestion(questionId);
	}

	@Test
	void rebuildEmbeddingRefreshesPublishedQuestion() {
		UUID questionId = UUID.randomUUID();
		QuestionDef question = questionDef();
		when(questionAdminRepository.findPublishedQuestionDefById(questionId)).thenReturn(Optional.of(question));
		when(questionAdminRepository.deactivateQuestion(questionId)).thenReturn(1);
		when(questionAdminRepository.activateQuestion(questionId)).thenReturn(1);

		QuestionPublicationService service = new QuestionPublicationService(questionAdminRepository, questionEmbeddingService);

		service.rebuildEmbedding(questionId);

		verify(questionAdminRepository).findPublishedQuestionDefById(questionId);
		verify(questionAdminRepository).deactivateQuestion(questionId);
		verify(questionEmbeddingService).buildAndStoreEmbedding(questionId, question);
		verify(questionAdminRepository).activateQuestion(questionId);
	}

	@Test
	void rebuildEmbeddingReturnsControlledFailureWhenEmbeddingBuildFails() {
		UUID questionId = UUID.randomUUID();
		QuestionDef question = questionDef();
		when(questionAdminRepository.findPublishedQuestionDefById(questionId)).thenReturn(Optional.of(question));
		when(questionAdminRepository.deactivateQuestion(questionId)).thenReturn(1);
		doThrow(new IllegalStateException("quota exhausted"))
			.when(questionEmbeddingService)
			.buildAndStoreEmbedding(questionId, question);

		QuestionPublicationService service = new QuestionPublicationService(questionAdminRepository, questionEmbeddingService);

		assertThatThrownBy(() -> service.rebuildEmbedding(questionId))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Question embedding rebuild unavailable");

		verify(questionAdminRepository).deactivateQuestion(questionId);
		verify(questionEmbeddingService).buildAndStoreEmbedding(questionId, question);
		verify(questionAdminRepository, org.mockito.Mockito.never()).activateQuestion(questionId);
	}

	@Test
	void rebuildEmbeddingRejectsUnpublishedQuestion() {
		UUID questionId = UUID.randomUUID();
		when(questionAdminRepository.findPublishedQuestionDefById(questionId)).thenReturn(Optional.empty());

		QuestionPublicationService service = new QuestionPublicationService(questionAdminRepository, questionEmbeddingService);

		assertThatThrownBy(() -> service.rebuildEmbedding(questionId))
			.isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

		verify(questionAdminRepository).findPublishedQuestionDefById(questionId);
		verifyNoInteractions(questionEmbeddingService);
	}

	private QuestionDef questionDef() {
		return new QuestionDef(
			"Kafka Partition Rebalancing",
			"Explain how Kafka partition rebalancing works.",
			"SYSTEM_DESIGN",
			"MEDIUM",
			"SENIOR",
			List.of("kafka", "spring-boot"),
			List.of("consumer groups"),
			Map.of("architecture", 20)
		);
	}
}
