package com.intervu.questionbank;

import com.intervu.questionbank.embedding.QuestionEmbeddingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class QuestionPublicationService {

	private final QuestionAdminRepository questionAdminRepository;
	private final QuestionEmbeddingService questionEmbeddingService;

	public QuestionPublicationService(
		QuestionAdminRepository questionAdminRepository,
		QuestionEmbeddingService questionEmbeddingService
	) {
		this.questionAdminRepository = questionAdminRepository;
		this.questionEmbeddingService = questionEmbeddingService;
	}

	public void publishQuestion(UUID questionId) {
		int updated = questionAdminRepository.publishQuestion(questionId);
		if (updated == 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question not found or not in DRAFT/REVIEWED status");
		}

		QuestionDef question = questionAdminRepository.findPublishedQuestionDefById(questionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Published question disappeared before embedding build"));

		try {
			questionEmbeddingService.buildAndStoreEmbedding(questionId, question);
		} catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Question embedding build unavailable", ex);
		}

		if (questionAdminRepository.activateQuestion(questionId) == 0) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Question embedding persisted but activation failed");
		}
	}

	public void rebuildEmbedding(UUID questionId) {
		QuestionDef question = questionAdminRepository.findPublishedQuestionDefById(questionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must be published before embedding rebuild"));

		if (questionAdminRepository.deactivateQuestion(questionId) == 0) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Question could not be paused before embedding rebuild");
		}

		try {
			questionEmbeddingService.buildAndStoreEmbedding(questionId, question);
		} catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Question embedding rebuild unavailable", ex);
		}

		if (questionAdminRepository.activateQuestion(questionId) == 0) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Question embedding persisted but activation failed");
		}
	}

	public void archiveQuestion(UUID questionId) {
		int updated = questionAdminRepository.archiveQuestion(questionId);
		if (updated == 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question not found");
		}
	}
}
