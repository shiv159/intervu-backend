package com.intervu.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record RegisterRequest(
		@NotBlank @Email String email,
		@NotBlank String password
	) {
	}

	public record LoginRequest(
		@NotBlank @Email String email,
		@NotBlank String password
	) {
	}

	public record RefreshRequest(
		@NotBlank String refreshToken
	) {
	}

	public record AuthResponse(
		String accessToken,
		String refreshToken,
		String userId,
		String email
	) {
	}

	public record AuthenticatedUser(
		String userId,
		String email
	) {
	}
}
