package com.intervu.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static com.intervu.auth.AuthDtos.AuthResponse;
import static com.intervu.auth.AuthDtos.AuthenticatedUser;
import static com.intervu.auth.AuthDtos.LoginRequest;
import static com.intervu.auth.AuthDtos.RefreshRequest;
import static com.intervu.auth.AuthDtos.RegisterRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String DEFAULT_JWT_SECRET = "defaultSuperSecretKeyForIntervuMvpThatIsLongEnoughToUseForHMACSHA256";

    private final Environment environment;
    private final String jwtSecret;
    private final AuthService authService;

    public AuthController(
        Environment environment,
        AuthService authService,
        @Value("${intervu.jwt.secret:" + DEFAULT_JWT_SECRET + "}") String jwtSecret
    ) {
        this.environment = environment;
        this.authService = authService;
        this.jwtSecret = jwtSecret;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @GetMapping("/me")
    public AuthenticatedUser me(@RequestAttribute("userId") String userId) {
        return authService.me(userId);
    }

    @GetMapping("/dev-token")
    public Map<String, String> getDevToken(@RequestParam(defaultValue = "dev-user-123") String userId) {
        if (isProduction()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                "dev-token is disabled outside development profiles");
        }
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000)) // 1 day
                .signWith(key)
                .compact();

        return Map.of("token", token);
    }

    private boolean isProduction() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
