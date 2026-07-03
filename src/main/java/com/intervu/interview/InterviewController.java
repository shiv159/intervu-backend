package com.intervu.interview;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

import static com.intervu.interview.InterviewDtos.AnswerSubmissionRequest;
import static com.intervu.interview.InterviewDtos.AnswerSubmissionResponse;
import static com.intervu.interview.InterviewDtos.CreateInterviewRequest;
import static com.intervu.interview.InterviewDtos.FeedbackResponse;
import static com.intervu.interview.InterviewDtos.InterviewSessionResponse;
import static com.intervu.interview.InterviewDtos.SessionEventResponse;

@RestController
@RequestMapping("/api/interviews")
@CrossOrigin(origins = "http://localhost:4200")
public class InterviewController {

	private final InterviewService interviewService;

	public InterviewController(InterviewService interviewService) {
		this.interviewService = interviewService;
	}

	@PostMapping
	public InterviewSessionResponse createInterview(
		@RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
		@Valid @RequestBody CreateInterviewRequest request
	) {
		return interviewService.createInterview(userId, request);
	}

	@GetMapping("/{sessionId}")
	public InterviewSessionResponse getInterview(
		@RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
		@PathVariable UUID sessionId
	) {
		return interviewService.getInterview(sessionId, userId);
	}

	@PostMapping("/{sessionId}/interactions")
	public AnswerSubmissionResponse submitAnswer(
		@RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@PathVariable UUID sessionId,
		@Valid @RequestBody AnswerSubmissionRequest request
	) {
		return interviewService.submitAnswer(sessionId, userId, idempotencyKey, request);
	}

	@GetMapping("/{sessionId}/feedback")
	public FeedbackResponse getFeedback(
		@RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
		@PathVariable UUID sessionId
	) {
		return interviewService.getFeedback(sessionId, userId);
	}

	@GetMapping("/{sessionId}/events")
	public List<SessionEventResponse> getEvents(
		@RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
		@PathVariable UUID sessionId,
		@RequestParam(name = "after", defaultValue = "0") long afterVersion
	) {
		return interviewService.getEvents(sessionId, userId, afterVersion);
	}

}
