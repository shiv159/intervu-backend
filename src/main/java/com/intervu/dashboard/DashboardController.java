package com.intervu.dashboard;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.intervu.dashboard.DashboardDtos.DashboardSessionSummary;
import static com.intervu.dashboard.DashboardDtos.SessionFeedbackResponse;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/sessions")
	public List<DashboardSessionSummary> listSessions(
		@RequestAttribute("userId") String userId,
		@RequestParam(name = "page", defaultValue = "0") int page,
		@RequestParam(name = "size", defaultValue = "20") int size
	) {
		return dashboardService.listSessions(userId, page, size);
	}

	@GetMapping("/sessions/{sessionId}/feedback")
	public SessionFeedbackResponse getSessionFeedback(
		@RequestAttribute("userId") String userId,
		@PathVariable UUID sessionId
	) {
		return dashboardService.getSessionFeedback(sessionId, userId);
	}

	@DeleteMapping("/sessions/{sessionId}")
	public void deleteSession(
		@RequestAttribute("userId") String userId,
		@PathVariable UUID sessionId
	) {
		dashboardService.deleteSession(sessionId, userId);
	}
}