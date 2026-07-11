package com.intervu.auth;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTests {

	private static final String TEST_SECRET = "test-secret-that-is-long-enough-for-hmac-sha256-abcdefghijklmnop";

	@Test
	void devTokenAvailableOutsideProduction() {
		Environment environment = mock(Environment.class);
		AuthService authService = mock(AuthService.class);
		when(environment.getActiveProfiles()).thenReturn(new String[]{});

		AuthController controller = new AuthController(environment, authService, TEST_SECRET);

		var result = controller.getDevToken("user-1");
		assertThat(result).containsKey("token");
	}

	@Test
	void devTokenBlockedInProduction() {
		Environment environment = mock(Environment.class);
		AuthService authService = mock(AuthService.class);
		when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

		AuthController controller = new AuthController(environment, authService, TEST_SECRET);

		assertThatThrownBy(() -> controller.getDevToken("user-1"))
			.isInstanceOf(ResponseStatusException.class)
			.hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);
	}
}
