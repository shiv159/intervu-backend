package com.intervu.questionbank.embedding;

import com.intervu.questionbank.QuestionDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionEmbeddingTextBuilderTests {

	private final QuestionEmbeddingTextBuilder builder = new QuestionEmbeddingTextBuilder();

	@Test
	void canonicalTextIncludesCoreQuestionFields() {
		QuestionDef question = new QuestionDef(
			"Kafka Partition Rebalancing",
			"Explain how Kafka partition rebalancing works.",
			"SYSTEM_DESIGN",
			"MEDIUM",
			"SENIOR",
			List.of("kafka", "spring-boot"),
			List.of("consumer groups"),
			Map.of("architecture", 20)
		);

		String canonicalText = builder.buildCanonicalText(question);

		assertThat(canonicalText).isEqualTo("""
			Title: Kafka Partition Rebalancing
			Prompt: Explain how Kafka partition rebalancing works.
			Mode: SYSTEM_DESIGN
			Difficulty: MEDIUM
			Seniority: SENIOR
			Tags: kafka, spring-boot
			Expected Concepts: consumer groups""");
	}

	@Test
	void hashChangesWhenEmbeddingInputsChange() {
		QuestionDef base = new QuestionDef(
			"Kafka Partition Rebalancing",
			"Explain how Kafka partition rebalancing works.",
			"SYSTEM_DESIGN",
			"MEDIUM",
			"SENIOR",
			List.of("kafka", "spring-boot"),
			List.of("consumer groups"),
			Map.of("architecture", 20)
		);

		QuestionDef changed = new QuestionDef(
			"Kafka Partition Rebalancing",
			"Explain how Kafka partition balancing works.",
			"SYSTEM_DESIGN",
			"MEDIUM",
			"SENIOR",
			List.of("kafka", "spring-boot", "streaming"),
			List.of("consumer groups"),
			Map.of("architecture", 20)
		);

		String baseHash = builder.hashCanonicalText(builder.buildCanonicalText(base));
		String changedHash = builder.hashCanonicalText(builder.buildCanonicalText(changed));

		assertThat(baseHash).isNotEqualTo(changedHash);
	}

	@Test
	void vectorSerializationProducesPgVectorLiteral() {
		String literal = builder.toPgVectorLiteral(List.of(0.125, -2.5, 3.0));

		assertThat(literal).isEqualTo("[0.125,-2.5,3]");
	}
}
