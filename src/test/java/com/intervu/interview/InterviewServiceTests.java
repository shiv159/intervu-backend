package com.intervu.interview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.intervu.resumejd.ResumeJdDtos;
import com.intervu.resumejd.ResumeJdRepository;

import static com.intervu.interview.InterviewDtos.AnswerSubmissionRequest;
import static com.intervu.interview.InterviewDtos.CreateInterviewRequest;
import static com.intervu.interview.InterviewDtos.EvaluationDraft;
import static com.intervu.interview.InterviewDtos.EvaluationRow;
import static com.intervu.interview.InterviewDtos.QuestionPayload;
import static com.intervu.interview.InterviewDtos.SessionRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTests {

	@Mock
	QuestionRepository questionRepository;

	@Mock
	QuestionRetrievalService questionRetrievalService;

	@Mock
	InterviewRepository interviewRepository;

	@Mock
	AnswerEvaluator answerEvaluator;

	@Mock
	ResumeJdRepository resumeJdRepository;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private InterviewService service() {
		return new InterviewService(
			questionRepository,
			questionRetrievalService,
			interviewRepository,
			answerEvaluator,
			objectMapper,
			"MOCK",
			Runnable::run,
			resumeJdRepository
		);
	}

	private QuestionPayload questionPayload(UUID id, int version) {
		return new QuestionPayload(
			id, "Question", "Prompt", "SYSTEM_DESIGN", "MEDIUM", "SENIOR",
			List.of("kafka", "caching"), List.of("architecture", "tradeoffs"), Map.of("architecture", 20), version
		);
	}

	private EvaluationDraft draft() {
		return new EvaluationDraft(
			80, Map.of("architecture", 16), List.of("clear"), List.of("depth"),
			"Why?", "mock-model", "mock-provider", 12L, 0.01, "eval-v1", "prompt-v1");
	}

	private EvaluationRow persistedEvaluation(UUID sessionId) {
		return new EvaluationRow(
			UUID.randomUUID(), sessionId, UUID.randomUUID(), 80,
			Map.of("architecture", 16), List.of("clear"), List.of("depth"),
			"Why?", "mock-model", "mock-provider", 12L, 0.01, "eval-v1", "prompt-v1");
	}

	@Test
	void resumeAndJdContextIsMergedIntoRetrieval() {
		UUID sessionId = UUID.randomUUID();
		UUID questionId = UUID.randomUUID();
		UUID resumeId = UUID.randomUUID();
		UUID jdId = UUID.randomUUID();
		QuestionPayload question = questionPayload(questionId, 7);

		ResumeJdDtos.ResumeExtract resume = new ResumeJdDtos.ResumeExtract(
			resumeId, "owner-1", "cv.pdf", "text", List.of("kafka", "redis"), List.of("scaling"),
			List.of(), "Backend Engineer", "SENIOR", "tika-1", false, Instant.now());
		ResumeJdDtos.JdExtract jd = new ResumeJdDtos.JdExtract(
			jdId, "owner-1", "jd text", List.of(), List.of("postgresql"), List.of(),
			"SENIOR", "tika-1", false, Instant.now());

		when(resumeJdRepository.findResumeById(resumeId)).thenReturn(Optional.of(resume));
		when(resumeJdRepository.findJdById(jdId)).thenReturn(Optional.of(jd));

		ArgumentCaptor<List<String>> skillsCaptor = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<List<String>> focusCaptor = ArgumentCaptor.forClass(List.class);
		when(questionRetrievalService.selectFirstQuestion(any(), any(), skillsCaptor.capture(), focusCaptor.capture()))
			.thenReturn(question);

		AtomicReference<SessionRow> stored = new AtomicReference<>();
		doAnswer(inv -> {
			stored.set(inv.getArgument(0));
			return null;
		}).when(interviewRepository).insertSession(any());
		when(interviewRepository.loadSession(any())).thenAnswer(inv -> stored.get());
		when(questionRepository.findById(any())).thenReturn(Optional.of(question));

		service().createInterview("owner-1", new CreateInterviewRequest(
			"Backend Engineer", "SENIOR", "CODE", List.of("java"), List.of("caching"), resumeId, jdId));

		assertThat(skillsCaptor.getValue()).contains("java", "kafka", "redis", "postgresql");
		assertThat(focusCaptor.getValue()).contains("caching", "scaling");
		assertThat(stored.get().skills()).contains("java", "kafka", "redis", "postgresql");
	}

	@Test
	void submitReturnsPendingWhileWaitingForEvaluation() {
		UUID sessionId = UUID.randomUUID();
		QuestionPayload question = questionPayload(UUID.randomUUID(), 3);
		SessionRow session = new SessionRow(
			sessionId, "owner-1", "Role", "IN_PROGRESS", "CODE", "SENIOR", "MEDIUM",
			List.of("java"), List.of("caching"), question.id(), question.version(), 1L);

		when(interviewRepository.loadSession(sessionId)).thenReturn(session);
		when(questionRepository.findById(question.id())).thenReturn(Optional.of(question));
		when(interviewRepository.findInteractionByIdempotencyKey(sessionId, "idem-1")).thenReturn(Optional.empty());
		when(answerEvaluator.evaluate(question, "answer")).thenReturn(draft());
		when(interviewRepository.findEvaluationByInteractionId(any())).thenReturn(Optional.of(persistedEvaluation(sessionId)));

		var response = service().submitAnswer(sessionId, "owner-1", "idem-1", new AnswerSubmissionRequest("answer"));

		assertThat(response.evaluationPending()).isTrue();
	}

	@Test
	void asyncSuccessEmitsSingleLiveScoreUpdatedEvent() {
		UUID sessionId = UUID.randomUUID();
		QuestionPayload question = questionPayload(UUID.randomUUID(), 3);
		SessionRow session = new SessionRow(
			sessionId, "owner-1", "Role", "IN_PROGRESS", "CODE", "SENIOR", "MEDIUM",
			List.of("java"), List.of("caching"), question.id(), question.version(), 1L);

		when(interviewRepository.loadSession(sessionId)).thenReturn(session);
		when(questionRepository.findById(question.id())).thenReturn(Optional.of(question));
		when(interviewRepository.findInteractionByIdempotencyKey(sessionId, "idem-1")).thenReturn(Optional.empty());
		when(answerEvaluator.evaluate(question, "answer")).thenReturn(draft());
		when(interviewRepository.findEvaluationByInteractionId(any())).thenReturn(Optional.of(persistedEvaluation(sessionId)));

		service().submitAnswer(sessionId, "owner-1", "idem-1", new AnswerSubmissionRequest("answer"));

		ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
		verify(interviewRepository, atLeastOnce()).insertEvent(eq(sessionId), eventTypeCaptor.capture(), any());
		assertThat(eventTypeCaptor.getAllValues().stream().filter("LIVE_SCORE_UPDATED"::equals).count()).isEqualTo(1);
	}

	@Test
	void asyncPersistenceFailureMarksEvaluationFailedNotEvaluated() {
		UUID sessionId = UUID.randomUUID();
		QuestionPayload question = questionPayload(UUID.randomUUID(), 3);
		SessionRow session = new SessionRow(
			sessionId, "owner-1", "Role", "IN_PROGRESS", "CODE", "SENIOR", "MEDIUM",
			List.of("java"), List.of("caching"), question.id(), question.version(), 1L);

		when(interviewRepository.loadSession(sessionId)).thenReturn(session);
		when(questionRepository.findById(question.id())).thenReturn(Optional.of(question));
		when(interviewRepository.findInteractionByIdempotencyKey(sessionId, "idem-1")).thenReturn(Optional.empty());
		when(answerEvaluator.evaluate(question, "answer")).thenReturn(draft());
		doThrow(new RuntimeException("db down"))
			.when(interviewRepository).insertEvaluation(any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), anyLong(), anyDouble(), any(), any());

		service().submitAnswer(sessionId, "owner-1", "idem-1", new AnswerSubmissionRequest("answer"));

		ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
		verify(interviewRepository, atLeastOnce()).updateSessionState(eq(sessionId), stateCaptor.capture());
		assertThat(stateCaptor.getAllValues()).contains("EVALUATION_FAILED");
		assertThat(stateCaptor.getAllValues()).doesNotContain("EVALUATED");

		ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
		verify(interviewRepository, atLeastOnce()).insertEvent(eq(sessionId), eventCaptor.capture(), any());
		assertThat(eventCaptor.getAllValues()).contains("EVALUATION_FAILED");
		assertThat(eventCaptor.getAllValues()).doesNotContain("LIVE_SCORE_UPDATED");
	}
}
