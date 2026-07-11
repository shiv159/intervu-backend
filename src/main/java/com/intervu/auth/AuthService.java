package com.intervu.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

import static com.intervu.auth.AuthDtos.AuthResponse;
import static com.intervu.auth.AuthDtos.AuthenticatedUser;
import static com.intervu.auth.AuthDtos.LoginRequest;
import static com.intervu.auth.AuthDtos.RefreshRequest;
import static com.intervu.auth.AuthDtos.RegisterRequest;

@Service
public class AuthService {

	private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
	private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
	private static final String DEFAULT_SECRET = "defaultSuperSecretKeyForIntervuMvpThatIsLongEnoughToUseForHMACSHA256";

	private final UserRepository userRepository;
	private final SecretKey signingKey;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public AuthService(
		UserRepository userRepository,
		@Value("${intervu.jwt.secret:" + DEFAULT_SECRET + "}") String jwtSecret
	) {
		this.userRepository = userRepository;
		this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
	}

	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.findByEmail(email).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
		}
		UserRepository.UserRecord user = userRepository.insert(email, passwordEncoder.encode(request.password()));
		return issueTokens(user);
	}

	public AuthResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		UserRepository.UserRecord user = userRepository.findByEmail(email)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
		if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
		}
		return issueTokens(user);
	}

	public AuthResponse refresh(RefreshRequest request) {
		Claims claims = parseToken(request.refreshToken(), "refresh");
		UserRepository.UserRecord user = userRepository.findById(claims.getSubject())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
		return issueTokens(user);
	}

	public AuthenticatedUser me(String userId) {
		UserRepository.UserRecord user = userRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
		return new AuthenticatedUser(user.id(), user.email());
	}

	private AuthResponse issueTokens(UserRepository.UserRecord user) {
		return new AuthResponse(
			createToken(user, "access", ACCESS_TOKEN_TTL),
			createToken(user, "refresh", REFRESH_TOKEN_TTL),
			user.id(),
			user.email()
		);
	}

	private String createToken(UserRepository.UserRecord user, String type, Duration ttl) {
		Date issuedAt = new Date();
		Date expiration = new Date(issuedAt.getTime() + ttl.toMillis());
		return Jwts.builder()
			.subject(user.id())
			.claims(Map.of(
				"type", type,
				"email", user.email()
			))
			.issuedAt(issuedAt)
			.expiration(expiration)
			.signWith(signingKey)
			.compact();
	}

	private Claims parseToken(String token, String expectedType) {
		try {
			Claims claims = Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
			if (!expectedType.equals(claims.get("type", String.class))) {
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type");
			}
			return claims;
		} catch (ResponseStatusException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
		}
	}

	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase();
	}
}
