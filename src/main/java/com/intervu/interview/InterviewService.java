package com.intervu.interview;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.intervu.interview.InterviewDtos.AnswerSubmissionRequest;
import static com.intervu.interview.InterviewDtos.AnswerSubmissionResponse;
import static com.intervu.interview.InterviewDtos.CreateInterviewRequest;
import static com.intervu.interview.InterviewDtos.EvaluationDraft;
import static com.intervu.interview.InterviewDtos.EvaluationRow;
import static com.intervu.interview.InterviewDtos.EvaluationSummary;
import static com.intervu.interview.InterviewDtos.FeedbackResponse;
import static com.intervu.interview.InterviewDtos.InterviewSessionResponse;
import static com.intervu.interview.InterviewDtos.InteractionRow;
import static com.intervu.interview.InterviewDtos.QuestionPayload;
import static com.intervu.interview.InterviewDtos.SessionEventResponse;
import static com.intervu.interview.InterviewDtos.SessionEventRow;
import static com.intervu.interview.InterviewDtos.SessionRow;

@Service
public class InterviewService {

	private static final String STATE_IN_PROGRESS = "IN_PROGRESS";
	private static final String STATE_EVALUATED = "EVALUATED";

	private final QuestionRepository questionRepository;
	private final InterviewRepository interviewRepository;
	private final AnswerEvaluator answerEvaluator;
	private final ObjectMapper objectMapper;

	public InterviewService(
		QuestionRepository questionRepository,
		InterviewRepository interviewRepository,
		AnswerEvaluator answerEvaluator,
		ObjectMapper objectMapper
	) {
		this.questionRepository = questionRepository;
		this.interviewRepository = interviewRepository;
		this.answerEvaluator = answerEvaluator;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public InterviewSessionResponse createInterview(String ownerId, CreateInterviewRequest request) {
		QuestionPayload question = questionRepository.findFirstPublishedQuestion(request.mode(), request.seniority())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No published question matched the requested mode"));

		UUID sessionId = UUID.randomUUID();
		SessionRow session = new SessionRow(
			sessionId,
			ownerId,
			request.targetRole().trim(),
			STATE_IN_PROGRESS,
			normalizeMode(request.mode()),
			normalizeSeniority(request.seniority()),
			question.difficulty(),
			safeList(request.skills()),
			safeList(request.focusAreas()),
			question.id(),
			1L
		);
		interviewRepository.insertSession(session);
		interviewRepository.insertEvent(sessionId, "INTERVIEW_CREATED", payload(Map.of(
			"sessionId", sessionId,
			"ownerId", ownerId,
			"targetRole", session.targetRole(),
			"questionId", question.id()
		)));
		interviewRepository.insertEvent(sessionId, "QUESTION_STARTED", payload(Map.of(
			"questionId", question.id(),
			"workspaceType", question.mode(),
			"questionTitle", question.title()
		)));
		return loadSessionView(sessionId, ownerId);
	}

	public InterviewSessionResponse getInterview(UUID sessionId, String ownerId) {
		return loadSessionView(sessionId, ownerId);
	}

	@Transactional
	public AnswerSubmissionResponse submitAnswer(UUID sessionId, String ownerId, String idempotencyKey, AnswerSubmissionRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
		}

		SessionRow session = requireOwnedSession(sessionId, ownerId);
		QuestionPayload question = loadCurrentQuestion(session);
		InteractionRow existingInteraction = interviewRepository.findInteractionByIdempotencyKey(sessionId, idempotencyKey)
			.orElse(null);

		if (existingInteraction != null) {
			EvaluationRow existingEvaluation = interviewRepository.findEvaluationByInteractionId(existingInteraction.id()).orElse(null);
			if (existingEvaluation == null) {
				existingEvaluation = storeEvaluation(sessionId, existingInteraction.id(), question, request.answer());
				interviewRepository.updateSessionState(sessionId, STATE_EVALUATED);
				interviewRepository.insertEvent(sessionId, "EVALUATION_COMPLETED", payload(Map.of(
					"interactionId", existingInteraction.id(),
					"evaluationId", existingEvaluation.id(),
					"score", existingEvaluation.score()
				)));
			}
			return new AnswerSubmissionResponse(existingInteraction.id(), loadSessionView(sessionId, ownerId), toSummary(existingEvaluation));
		}

		UUID interactionId = UUID.randomUUID();
		interviewRepository.insertInteraction(
			interactionId,
			sessionId,
			question.id(),
			idempotencyKey,
			"ANSWER",
			payload(Map.of(
				"answer", safeAnswer(request.answer()),
				"questionId", question.id(),
				"sessionId", sessionId
			))
		);
		interviewRepository.insertEvent(sessionId, "ANSWER_SUBMITTED", payload(Map.of(
			"interactionId", interactionId,
			"questionId", question.id()
		)));

		EvaluationRow evaluation = storeEvaluation(sessionId, interactionId, question, request.answer());
		interviewRepository.updateSessionState(sessionId, STATE_EVALUATED);
		interviewRepository.insertEvent(sessionId, "EVALUATION_COMPLETED", payload(Map.of(
			"interactionId", interactionId,
			"evaluationId", evaluation.id(),
			"score", evaluation.score()
		)));

		return new AnswerSubmissionResponse(interactionId, loadSessionView(sessionId, ownerId), toSummary(evaluation));
	}

	public FeedbackResponse getFeedback(UUID sessionId, String ownerId) {
		SessionRow session = requireOwnedSession(sessionId, ownerId);
		EvaluationRow evaluation = interviewRepository.findLatestEvaluationBySessionId(session.id()).orElse(null);
		if (evaluation == null) {
			return new FeedbackResponse(session.id(), "The interview is in progress. Submit an answer to generate feedback.", null, List.of(), List.of(), null);
		}
		return new FeedbackResponse(
			session.id(),
			buildSummary(evaluation.score(), session),
			evaluation.score(),
			evaluation.strengths(),
			evaluation.gaps(),
			evaluation.followUpQuestion()
		);
	}

	public List<SessionEventResponse> getEvents(UUID sessionId, String ownerId, long afterVersion) {
		SessionRow session = requireOwnedSession(sessionId, ownerId);
		return interviewRepository.findEventsAfter(session.id(), Math.max(0, afterVersion))
			.stream()
			.map(this::toEventResponse)
			.toList();
	}

	private EvaluationRow storeEvaluation(UUID sessionId, UUID interactionId, QuestionPayload question, String answer) {
		EvaluationDraft draft = answerEvaluator.evaluate(question, answer);
		UUID evaluationId = UUID.randomUUID();
		interviewRepository.insertEvaluation(
			evaluationId,
			sessionId,
			interactionId,
			draft.score(),
			draft.rubricScores(),
			draft.strengths(),
			draft.gaps(),
			draft.followUpQuestion()
		);
		interviewRepository.insertEvent(sessionId, "LIVE_SCORE_UPDATED", payload(Map.of(
			"interactionId", interactionId,
			"evaluationId", evaluationId,
			"score", draft.score()
		)));
		return interviewRepository.findEvaluationByInteractionId(interactionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Evaluation write did not persist"));
	}

	private InterviewSessionResponse loadSessionView(UUID sessionId, String ownerId) {
		SessionRow session = requireOwnedSession(sessionId, ownerId);
		QuestionPayload question = loadCurrentQuestion(session);
		EvaluationRow evaluation = interviewRepository.findLatestEvaluationBySessionId(session.id()).orElse(null);
		return new InterviewSessionResponse(
			session.id(),
			session.ownerId(),
			session.targetRole(),
			session.state(),
			session.mode(),
			session.seniority(),
			session.difficulty(),
			session.stateVersion(),
			session.skills(),
			session.focusAreas(),
			question,
			evaluation == null ? null : toSummary(evaluation)
		);
	}

	private SessionRow requireOwnedSession(UUID sessionId, String ownerId) {
		SessionRow session = interviewRepository.loadSession(sessionId);
		if (!session.ownerId().equals(ownerId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to the current user");
		}
		return session;
	}

	private QuestionPayload loadCurrentQuestion(SessionRow session) {
		if (session.currentQuestionId() == null) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Interview session is missing the current question");
		}
		return questionRepository.findById(session.currentQuestionId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Question referenced by the session was not found"));
	}

	private EvaluationSummary toSummary(EvaluationRow row) {
		return new EvaluationSummary(
			row.id(),
			row.score(),
			row.rubricScores(),
			row.strengths(),
			row.gaps(),
			row.followUpQuestion()
		);
	}

	private SessionEventResponse toEventResponse(SessionEventRow row) {
		return new SessionEventResponse(row.eventVersion(), row.eventType(), row.payload());
	}

	private String buildSummary(int score, SessionRow session) {
		String band = score >= 85 ? "strong" : score >= 65 ? "solid" : score >= 45 ? "mixed" : "needs work";
		return "This " + session.mode().toLowerCase() + " interview is " + band + " so far, with an overall score of " + score + ".";
	}

	private String normalizeMode(String mode) {
		return mode.trim().toUpperCase().replace(' ', '_');
	}

	private String normalizeSeniority(String seniority) {
		return seniority.trim().toUpperCase().replace(' ', '_');
	}

	private List<String> safeList(List<String> input) {
		return input == null ? List.of() : List.copyOf(input);
	}

	private String safeAnswer(String answer) {
		return answer == null ? "" : answer;
	}

	private String payload(Map<String, ?> payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialise event payload", ex);
		}
	}

}
