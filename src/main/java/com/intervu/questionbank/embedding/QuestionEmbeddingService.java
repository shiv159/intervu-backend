package com.intervu.questionbank.embedding;

import com.intervu.questionbank.QuestionDef;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class QuestionEmbeddingService {

	private final QuestionEmbeddingTextBuilder textBuilder;
	private final EmbeddingClient embeddingClient;
	private final QuestionEmbeddingRepository embeddingRepository;

	public QuestionEmbeddingService(
		QuestionEmbeddingTextBuilder textBuilder,
		EmbeddingClient embeddingClient,
		QuestionEmbeddingRepository embeddingRepository
	) {
		this.textBuilder = textBuilder;
		this.embeddingClient = embeddingClient;
		this.embeddingRepository = embeddingRepository;
	}

	public void buildAndStoreEmbedding(UUID questionId, QuestionDef question) {
		String canonicalText = textBuilder.buildCanonicalText(question);
		String embeddedTextHash = textBuilder.hashCanonicalText(canonicalText);
		EmbeddingResult result = embeddingClient.embed(canonicalText);
		embeddingRepository.upsertEmbedding(
			questionId,
			textBuilder.toPgVectorLiteral(result.embedding()),
			result.model(),
			embeddedTextHash
		);
	}
}
