package com.intervu.questionbank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankImportServiceTests {

	@TempDir
	Path tempDir;

	@Mock
	QuestionAdminRepository questionAdminRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void importQuestionsImportsValidJsonAsDraftAndNormalizesTags() throws Exception {
		Files.writeString(tempDir.resolve("kafka-rebalancing.json"), """
			{
			  "title": "Kafka Partition Rebalancing",
			  "prompt": "Explain how Kafka partition rebalancing works.",
			  "mode": "system_design",
			  "difficulty": "medium",
			  "seniority": "senior",
			  "tags": ["Kafka", " Spring Boot "],
			  "expectedConcepts": ["consumer groups"],
			  "rubric": {"architecture": 20}
			}
			""");

		QuestionBankImportService service = new QuestionBankImportService(
			tempDir.toString(),
			questionAdminRepository,
			objectMapper
		);

		ImportReport report = service.importQuestions();

		assertThat(report.importedCount()).isEqualTo(1);
		assertThat(report.failedCount()).isZero();
		assertThat(report.skippedCount()).isZero();
		assertThat(report.errors()).isEmpty();

		ArgumentCaptor<QuestionDef> defCaptor = ArgumentCaptor.forClass(QuestionDef.class);
		verify(questionAdminRepository).exists("Kafka Partition Rebalancing", "SYSTEM_DESIGN", "SENIOR");
		verify(questionAdminRepository).insertDraftQuestion(org.mockito.ArgumentMatchers.any(), defCaptor.capture());

		QuestionDef normalized = defCaptor.getValue();
		assertThat(normalized.mode()).isEqualTo("SYSTEM_DESIGN");
		assertThat(normalized.difficulty()).isEqualTo("MEDIUM");
		assertThat(normalized.seniority()).isEqualTo("SENIOR");
		assertThat(normalized.tags()).containsExactly("kafka", "spring-boot");
	}

	@Test
	void importQuestionsRejectsInvalidJsonWithoutCallingRepository() throws Exception {
		Files.writeString(tempDir.resolve("invalid.json"), """
			{
			  "prompt": "Explain Kafka."
			}
			""");

		QuestionBankImportService service = new QuestionBankImportService(
			tempDir.toString(),
			questionAdminRepository,
			objectMapper
		);

		ImportReport report = service.importQuestions();

		assertThat(report.importedCount()).isZero();
		assertThat(report.failedCount()).isEqualTo(1);
		assertThat(report.skippedCount()).isZero();
		assertThat(report.errors()).hasSize(1);
		assertThat(report.errors().getFirst()).contains("Missing title", "Missing mode");

		verify(questionAdminRepository, never()).exists(
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString()
		);
		verify(questionAdminRepository, never()).insertDraftQuestion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void importQuestionsSkipsDuplicatesByTitle() throws Exception {
		Files.writeString(tempDir.resolve("duplicate.json"), """
			{
			  "title": "Duplicate Question",
			  "prompt": "Explain Kafka.",
			  "mode": "code",
			  "difficulty": "easy",
			  "seniority": "junior",
			  "tags": ["Java"],
			  "expectedConcepts": ["concept"],
			  "rubric": {"score": 100}
			}
			""");

		when(questionAdminRepository.exists("Duplicate Question", "CODE", "JUNIOR")).thenReturn(true);

		QuestionBankImportService service = new QuestionBankImportService(
			tempDir.toString(),
			questionAdminRepository,
			objectMapper
		);

		ImportReport report = service.importQuestions();

		assertThat(report.importedCount()).isZero();
		assertThat(report.failedCount()).isZero();
		assertThat(report.skippedCount()).isEqualTo(1);
		assertThat(report.errors()).isEmpty();

		verify(questionAdminRepository).exists("Duplicate Question", "CODE", "JUNIOR");
		verify(questionAdminRepository, never()).insertDraftQuestion(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}
}
