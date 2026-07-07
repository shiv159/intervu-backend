package com.intervu.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.intervu.dashboard.DashboardDtos.DashboardSessionSummary;
import static com.intervu.dashboard.DashboardDtos.RoundFeedback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTests {

	@Mock
	private DashboardRepository repository;

	@InjectMocks
	private DashboardService service;

	@Test
	void listSessionsDelegatesToRepository() {
		var ownerId = "test-user";
		var sessionId = UUID.randomUUID();
		var summary = new DashboardSessionSummary(
			sessionId, "Backend Engineer", "CODE", "SENIOR", "COMPLETED",
			75, "Solid", 5L, Instant.now(), Instant.now()
		);
		when(repository.findSessionsByOwner(ownerId, 0, 20)).thenReturn(List.of(summary));

		var result = service.listSessions(ownerId, 0, 20);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).sessionId()).isEqualTo(sessionId);
		assertThat(result.get(0).targetRole()).isEqualTo("Backend Engineer");
	}

	@Test
	void listSessionsReturnsEmptyWhenNoSessions() {
		var ownerId = "test-user";
		when(repository.findSessionsByOwner(ownerId, 0, 20)).thenReturn(Collections.emptyList());

		var result = service.listSessions(ownerId, 0, 20);

		assertThat(result).isEmpty();
	}

	@Test
	void getSessionFeedbackAggregatesCorrectly() {
		var sessionId = UUID.randomUUID();
		var ownerId = "test-user";
		var rounds = List.of(
			new RoundFeedback(
				UUID.randomUUID(), "Two Sum", 80,
				Map.of("correctness", 85, "efficiency", 75),
				List.of("Good approach"), List.of("Missing edge cases"), "CODE"
			),
			new RoundFeedback(
				UUID.randomUUID(), "Binary Search", 60,
				Map.of("correctness", 70, "efficiency", 50),
				List.of("Clear thinking"), List.of("Time complexity wrong"), "CODE"
			)
		);
		when(repository.findEvaluationsBySessionId(sessionId)).thenReturn(rounds);
		when(repository.findSessionCreatedAt(sessionId)).thenReturn(Optional.of(Instant.now()));
		when(repository.findSessionCompletedAt(sessionId)).thenReturn(Optional.empty());

		var feedback = service.getSessionFeedback(sessionId, ownerId);

		assertThat(feedback.sessionId()).isEqualTo(sessionId);
		assertThat(feedback.overallScore()).isEqualTo(70); // avg of 80, 60
		assertThat(feedback.overallReadiness()).isEqualTo("Solid");
		assertThat(feedback.rounds()).hasSize(2);
		assertThat(feedback.strengths()).contains("Good approach", "Clear thinking");
		assertThat(feedback.areasForGrowth()).contains("Missing edge cases", "Time complexity wrong");
		assertThat(feedback.topicMastery()).containsKeys("correctness", "efficiency");
	}

	@Test
	void getSessionFeedbackThrowsWhenNoEvaluations() {
		var sessionId = UUID.randomUUID();
		when(repository.findEvaluationsBySessionId(sessionId)).thenReturn(Collections.emptyList());

		assertThatThrownBy(() -> service.getSessionFeedback(sessionId, "test-user"))
			.isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
	}

	@Test
	void deleteSessionDelegatesToRepository() {
		var sessionId = UUID.randomUUID();
		service.deleteSession(sessionId, "test-user");
		// No exception means success
	}
}