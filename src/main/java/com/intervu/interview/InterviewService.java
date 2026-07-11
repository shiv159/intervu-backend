package com.intervu.interview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.intervu.resumejd.ResumeJdRepository;
import com.intervu.resumejd.ResumeJdDtos;

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

	private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

	private static final String STATE_IN_PROGRESS = "IN_PROGRESS";
	private static final String STATE_EVALUATED = "EVALUATED";
	private static final String STATE_WAITING_EVALUATION = "WAITING_EVALUATION";
	private static final String STATE_EVALUATION_FAILED = "EVALUATION_FAILED";

	private final QuestionRepository questionRepository;
	private final QuestionRetrievalService questionRetrievalService;
	private final InterviewRepository interviewRepository;
	private final AnswerEvaluator answerEvaluator;
	private final ObjectMapper objectMapper;
	private final String aiMode;
	private final Executor evaluationExecutor;
	private final ResumeJdRepository resumeJdRepository;

	public InterviewService(
		QuestionRepository questionRepository,
		QuestionRetrievalService questionRetrievalService,
		InterviewRepository interviewRepository,
		AnswerEvaluator answerEvaluator,
		ObjectMapper objectMapper,
		@Value("${intervu.ai.mode:MOCK}") String aiMode,
		Executor evaluationExecutor,
		ResumeJdRepository resumeJdRepository
	) {
		this.questionRepository = questionRepository;
		this.questionRetrievalService = questionRetrievalService;
		this.interviewRepository = interviewRepository;
		this.answerEvaluator = answerEvaluator;
		this.objectMapper = objectMapper;
		this.aiMode = aiMode;
		this.evaluationExecutor = evaluationExecutor;
		this.resumeJdRepository = resumeJdRepository;
	}

	@Transactional
	public InterviewSessionResponse createInterview(String ownerId, CreateInterviewRequest request) {
		Map<String, String> adaptiveState = new LinkedHashMap<>();

		Set<String> mergedSkills = new LinkedHashSet<>(safeList(request.skills()));
		Set<String> mergedFocusAreas = new LinkedHashSet<>(safeList(request.focusAreas()));
		String resumeTargetRole = null;
		String resumeSeniority = null;
		String jdSeniority = null;

		if (request.resumeExtractId() != null) {
			ResumeJdDtos.ResumeExtract resume = resumeJdRepository.findResumeById(request.resumeExtractId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume extract not found"));
			if (resume.deleted() || !resume.ownerId().equals(ownerId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resume extract does not belong to the current user");
			}
			mergedSkills.addAll(safeList(resume.skills()));
			mergedFocusAreas.addAll(safeList(resume.focusAreas()));
			resumeTargetRole = resume.targetRole();
			resumeSeniority = resume.seniority();
			adaptiveState.put("resumeExtractId", request.resumeExtractId().toString());
			adaptiveState.put("resumeTargetRole", resumeTargetRole);
			adaptiveState.put("resumeSeniority", resumeSeniority);
		}
		if (request.jdExtractId() != null) {
			ResumeJdDtos.JdExtract jd = resumeJdRepository.findJdById(request.jdExtractId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Job description extract not found"));
			if (jd.deleted() || !jd.ownerId().equals(ownerId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Job description extract does not belong to the current user");
			}
			mergedSkills.addAll(safeList(jd.technologies()));
			jdSeniority = jd.seniority();
			adaptiveState.put("jdExtractId", request.jdExtractId().toString());
			adaptiveState.put("jdSeniority", jdSeniority);
		}

		adaptiveState.put("skills", String.join(",", mergedSkills));
		adaptiveState.put("focusAreas", String.join(",", mergedFocusAreas));

		String mode = normalizeMode(request.mode());
		String targetRole = (request.targetRole() != null && !request.targetRole().trim().isEmpty())
			? request.targetRole().trim()
			: resumeTargetRole;
		String seniority = pickSeniority(request.seniority(), resumeSeniority, jdSeniority);

		QuestionPayload question = questionRetrievalService.selectFirstQuestion(
			mode, seniority, List.copyOf(mergedSkills), List.copyOf(mergedFocusAreas));

		UUID sessionId = UUID.randomUUID();
		SessionRow session = new SessionRow(
			sessionId,
			ownerId,
			targetRole == null ? "" : targetRole,
			STATE_IN_PROGRESS,
			mode,
			normalizeSeniority(seniority),
			question.difficulty(),
			List.copyOf(mergedSkills),
			List.copyOf(mergedFocusAreas),
			question.id(),
			question.version(),
			1L
		);
		interviewRepository.insertSession(session);
		if (!adaptiveState.isEmpty()) {
			try {
				interviewRepository.updateSessionAdaptiveState(sessionId, objectMapper.writeValueAsString(adaptiveState));
			} catch (Exception ex) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to persist adaptive state", ex);
			}
		}
		interviewRepository.insertEvent(sessionId, "INTERVIEW_CREATED", payload(Map.of(
			"sessionId", sessionId,
			"ownerId", ownerId,
			"targetRole", session.targetRole(),
			"questionId", question.id(),
			"questionVersion", question.version()
		)));
		interviewRepository.insertEvent(sessionId, "QUESTION_STARTED", payload(Map.of(
			"questionId", question.id(),
			"questionVersion", question.version(),
			"workspaceType", question.mode(),
			"questionTitle", question.title()
		)));
		return loadSessionView(sessionId, ownerId);
	}

	public InterviewSessionResponse getInterview(UUID sessionId, String ownerId) {
		return loadSessionView(sessionId, ownerId);
	}

	@Transactional
	public InterviewSessionResponse nextQuestion(UUID sessionId, String ownerId) {
		SessionRow session = requireOwnedSession(sessionId, ownerId);
		if (!STATE_EVALUATED.equals(session.state())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot move to next question unless current answer is evaluated");
		}

		List<UUID> answeredQuestions = interviewRepository.findAnsweredQuestionIdsBySessionId(sessionId);
		QuestionPayload nextQ = questionRetrievalService.selectNextQuestion(session.mode(), session.seniority(), session.skills(), session.focusAreas(), answeredQuestions);

		if (nextQ != null) {
			interviewRepository.updateSessionCurrentQuestion(sessionId, nextQ.id(), nextQ.version());
			interviewRepository.updateSessionState(sessionId, STATE_IN_PROGRESS);
			interviewRepository.insertEvent(sessionId, "NEXT_QUESTION_READY", payload(Map.of(
				"questionId", nextQ.id(),
				"questionVersion", nextQ.version(),
				"workspaceType", nextQ.mode(),
				"questionTitle", nextQ.title()
			)));
		} else {
			interviewRepository.updateSessionState(sessionId, "COMPLETED");
			interviewRepository.insertEvent(sessionId, "INTERVIEW_COMPLETED", payload(Map.of(
				"sessionId", sessionId
			)));
		}
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
			if (existingEvaluation != null) {
				return new AnswerSubmissionResponse(existingInteraction.id(), loadSessionView(sessionId, ownerId), toSummary(existingEvaluation), false);
			}
			if (STATE_WAITING_EVALUATION.equals(session.state())) {
				return new AnswerSubmissionResponse(existingInteraction.id(), loadSessionView(sessionId, ownerId), null, true);
			}
			return new AnswerSubmissionResponse(existingInteraction.id(), loadSessionView(sessionId, ownerId), null, false);
		}

		if (!STATE_IN_PROGRESS.equals(session.state()) && !STATE_WAITING_EVALUATION.equals(session.state())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot submit answer unless session is in progress or waiting for evaluation");
		}

		UUID interactionId = UUID.randomUUID();
		interviewRepository.insertInteraction(
			interactionId,
			sessionId,
			question.id(),
			question.version(),
			idempotencyKey,
			"ANSWER",
			payload(Map.of(
				"answer", safeAnswer(request.answer()),
				"questionId", question.id(),
				"questionVersion", question.version(),
				"sessionId", sessionId
			))
		);
		interviewRepository.insertEvent(sessionId, "ANSWER_SUBMITTED", payload(Map.of(
			"interactionId", interactionId,
			"questionId", question.id(),
			"questionVersion", question.version()
		)));

		interviewRepository.updateSessionState(sessionId, STATE_WAITING_EVALUATION);

		evaluationExecutor.execute(() -> asyncEvaluate(sessionId, interactionId, question, request.answer()));

		return new AnswerSubmissionResponse(interactionId, loadSessionView(sessionId, ownerId), null, true);
	}

	private void asyncEvaluate(UUID sessionId, UUID interactionId, QuestionPayload question, String answer) {
		EvaluationDraft draft;
		try {
			draft = answerEvaluator.evaluate(question, answer);
		} catch (Exception ex) {
			log.warn("Evaluator failed for interaction {}: {}", interactionId, ex.getMessage());
			draft = answerEvaluator.evaluateFallback(question, answer);
		}

		try {
			EvaluationRow evaluation = persistEvaluation(sessionId, interactionId, draft);
			interviewRepository.updateSessionState(sessionId, STATE_EVALUATED);
			interviewRepository.insertEvent(sessionId, "EVALUATION_COMPLETED", payload(Map.of(
				"interactionId", interactionId,
				"evaluationId", evaluation.id(),
				"score", evaluation.score()
			)));
			interviewRepository.insertEvent(sessionId, "LIVE_SCORE_UPDATED", payload(Map.of(
				"interactionId", interactionId,
				"evaluationId", evaluation.id(),
				"score", evaluation.score()
			)));
		} catch (Exception dbEx) {
			log.error("Failed to persist evaluation for interaction {}: {}", interactionId, dbEx.getMessage());
			interviewRepository.updateSessionState(sessionId, STATE_EVALUATION_FAILED);
			interviewRepository.insertEvent(sessionId, "EVALUATION_FAILED", payload(Map.of(
				"interactionId", interactionId,
				"reason", "persistence-failure"
			)));
		}
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

	private EvaluationRow persistEvaluation(UUID sessionId, UUID interactionId, EvaluationDraft eval) {
		UUID evaluationId = UUID.randomUUID();
		interviewRepository.insertEvaluation(
			evaluationId,
			sessionId,
			interactionId,
			eval.score(),
			eval.rubricScores(),
			eval.strengths(),
			eval.gaps(),
			eval.followUpQuestion(),
			eval.model(),
			eval.provider(),
			eval.latencyMs(),
			eval.cost(),
			eval.evaluatorVersion(),
			eval.promptVersion()
		);
		return interviewRepository.findEvaluationByInteractionId(interactionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Evaluation write did not persist"));
	}

	private InterviewSessionResponse loadSessionView(UUID sessionId, String ownerId) {
		SessionRow session = requireOwnedSession(sessionId, ownerId);
		QuestionPayload question = null;
		if (session.currentQuestionId() != null) {
			question = loadCurrentQuestion(session);
		}
		EvaluationRow evaluation = null;
		if (STATE_EVALUATED.equals(session.state())) {
			evaluation = interviewRepository.findLatestEvaluationBySessionId(session.id()).orElse(null);
		}
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
			session.currentQuestionVersion(),
			question,
			evaluation == null ? null : toSummary(evaluation),
			aiMode
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
			row.followUpQuestion(),
			row.provider(),
			row.model(),
			row.latencyMs(),
			row.cost(),
			row.evaluatorVersion(),
			row.promptVersion()
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
		return mode == null ? "" : mode.trim().toUpperCase().replace(' ', '_');
	}

	private String normalizeSeniority(String seniority) {
		return seniority == null ? "" : seniority.trim().toUpperCase().replace(' ', '_');
	}

	private String pickSeniority(String primary, String... fallbacks) {
		if (primary != null && !primary.isBlank() && isValidSeniority(primary)) {
			return primary;
		}
		for (String fallback : fallbacks) {
			if (fallback != null && !fallback.isBlank() && isValidSeniority(fallback)) {
				return fallback;
			}
		}
		return primary;
	}

	private boolean isValidSeniority(String seniority) {
		return Set.of("JUNIOR", "MID", "SENIOR", "STAFF").contains(normalizeSeniority(seniority));
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
