package com.intervu.interview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.intervu.interview.InterviewDtos.AnswerSubmissionRequest;
import static com.intervu.interview.InterviewDtos.EvaluationDraft;
import static com.intervu.interview.InterviewDtos.EvaluationRow;
import static com.intervu.interview.InterviewDtos.QuestionPayload;
import static com.intervu.interview.InterviewDtos.SessionRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewVersionPersistenceTests {

	@Mock
	QuestionRepository questionRepository;

	@Mock
	QuestionRetrievalService questionRetrievalService;

	@Mock
	InterviewRepository interviewRepository;

	@Mock
	AnswerEvaluator answerEvaluator;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createInterviewPersistsCurrentQuestionVersionOnSessionRow() {
		UUID questionId = UUID.randomUUID();
		QuestionPayload question = questionPayload(questionId, 7);
		AtomicReference<SessionRow> storedSession = new AtomicReference<>();

		when(questionRetrievalService.selectFirstQuestion(anyString(), anyString(), anyList(), anyList())).thenReturn(question);
		when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
		doAnswer(invocation -> {
			storedSession.set(invocation.getArgument(0));
			return null;
		}).when(interviewRepository).insertSession(any());
		when(interviewRepository.loadSession(any())).thenAnswer(invocation -> storedSession.get());

		InterviewService service = new InterviewService(
			questionRepository,
			questionRetrievalService,
			interviewRepository,
			answerEvaluator,
			objectMapper
		);

		var response = service.createInterview("owner-1", new InterviewDtos.CreateInterviewRequest(
			"Platform Engineer",
			"SENIOR",
			"SYSTEM_DESIGN",
			List.of("java"),
			List.of("caching")
		));

		assertThat(response.currentQuestionVersion()).isEqualTo(7);
		assertThat(storedSession.get().currentQuestionVersion()).isEqualTo(7);
	}

	@Test
	void submitAnswerPersistsQuestionVersionOnInteractionRow() {
		UUID sessionId = UUID.randomUUID();
		QuestionPayload question = questionPayload(UUID.randomUUID(), 11);
		SessionRow session = new SessionRow(
			sessionId,
			"owner-1",
			"Platform Engineer",
			"IN_PROGRESS",
			"SYSTEM_DESIGN",
			"SENIOR",
			question.difficulty(),
			List.of("java"),
			List.of("caching"),
			question.id(),
			question.version(),
			1L
		);
		EvaluationDraft evaluationDraft = new EvaluationDraft(
			88,
			Map.of("architecture", 18),
			List.of("clear tradeoffs"),
			List.of("deeper detail"),
			"How would you scale it?",
			"mock-model",
			"mock-provider",
			12L,
			0.01
		);
		EvaluationRow persistedEvaluation = new EvaluationRow(
			UUID.randomUUID(),
			sessionId,
			UUID.randomUUID(),
			88,
			Map.of("architecture", 18),
			List.of("clear tradeoffs"),
			List.of("deeper detail"),
			"How would you scale it?",
			"mock-model",
			"mock-provider",
			12L,
			0.01
		);

		when(interviewRepository.loadSession(sessionId)).thenReturn(session);
		when(questionRepository.findById(question.id())).thenReturn(Optional.of(question));
		when(interviewRepository.findInteractionByIdempotencyKey(sessionId, "idem-1")).thenReturn(Optional.empty());
		when(answerEvaluator.evaluate(question, "answer")).thenReturn(evaluationDraft);
		when(interviewRepository.findEvaluationByInteractionId(any())).thenReturn(Optional.of(persistedEvaluation));

		InterviewService service = new InterviewService(
			questionRepository,
			questionRetrievalService,
			interviewRepository,
			answerEvaluator,
			objectMapper
		);

		service.submitAnswer(sessionId, "owner-1", "idem-1", new AnswerSubmissionRequest("answer"));

		ArgumentCaptor<Integer> questionVersionCaptor = ArgumentCaptor.forClass(Integer.class);
		verify(interviewRepository).insertInteraction(any(), any(), any(), questionVersionCaptor.capture(), any(), any(), any());
		assertThat(questionVersionCaptor.getValue()).isEqualTo(11);
	}

	@Test
	void nextQuestionPersistsQuestionVersionOnSessionUpdate() {
		UUID sessionId = UUID.randomUUID();
		QuestionPayload nextQuestion = questionPayload(UUID.randomUUID(), 13);
		AtomicReference<SessionRow> sessionRef = new AtomicReference<>(new SessionRow(
			sessionId,
			"owner-1",
			"Platform Engineer",
			"EVALUATED",
			"SYSTEM_DESIGN",
			"SENIOR",
			"MEDIUM",
			List.of("java"),
			List.of("caching"),
			UUID.randomUUID(),
			1,
			2L
		));
		ArgumentCaptor<UUID> sessionIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<UUID> questionIdCaptor = ArgumentCaptor.forClass(UUID.class);
		ArgumentCaptor<Integer> questionVersionCaptor = ArgumentCaptor.forClass(Integer.class);

		when(interviewRepository.loadSession(sessionId)).thenAnswer(invocation -> sessionRef.get());
		when(interviewRepository.findAnsweredQuestionIdsBySessionId(sessionId)).thenReturn(List.of(UUID.randomUUID()));
		when(questionRetrievalService.selectNextQuestion(anyString(), anyString(), anyList(), anyList(), anyList()))
			.thenReturn(nextQuestion);
		when(questionRepository.findById(nextQuestion.id())).thenReturn(Optional.of(nextQuestion));
		doAnswer(invocation -> {
			SessionRow current = sessionRef.get();
			sessionRef.set(new SessionRow(
				current.id(),
				current.ownerId(),
				current.targetRole(),
				"IN_PROGRESS",
				current.mode(),
				current.seniority(),
				current.difficulty(),
				current.skills(),
				current.focusAreas(),
				invocation.getArgument(1),
				invocation.getArgument(2),
				current.stateVersion() + 1
			));
			return null;
		}).when(interviewRepository).updateSessionCurrentQuestion(any(), any(), any());
		doAnswer(invocation -> {
			SessionRow current = sessionRef.get();
			sessionRef.set(new SessionRow(
				current.id(),
				current.ownerId(),
				current.targetRole(),
				"IN_PROGRESS",
				current.mode(),
				current.seniority(),
				current.difficulty(),
				current.skills(),
				current.focusAreas(),
				current.currentQuestionId(),
				current.currentQuestionVersion(),
				current.stateVersion() + 1
			));
			return null;
		}).when(interviewRepository).updateSessionState(any(), anyString());

		InterviewService service = new InterviewService(
			questionRepository,
			questionRetrievalService,
			interviewRepository,
			answerEvaluator,
			objectMapper
		);

		service.nextQuestion(sessionId, "owner-1");

		verify(interviewRepository).updateSessionCurrentQuestion(sessionIdCaptor.capture(), questionIdCaptor.capture(), questionVersionCaptor.capture());
		assertThat(sessionIdCaptor.getValue()).isEqualTo(sessionId);
		assertThat(questionIdCaptor.getValue()).isEqualTo(nextQuestion.id());
		assertThat(questionVersionCaptor.getValue()).isEqualTo(13);
		assertThat(sessionRef.get().currentQuestionVersion()).isEqualTo(13);
	}

	private QuestionPayload questionPayload(UUID id, int version) {
		return new QuestionPayload(
			id,
			"Question " + id.toString().substring(0, 8),
			"Prompt " + id.toString().substring(0, 8),
			"SYSTEM_DESIGN",
			"MEDIUM",
			"SENIOR",
			List.of("kafka", "caching"),
			List.of("architecture", "tradeoffs"),
			Map.of("architecture", 20),
			version
		);
	}
}
