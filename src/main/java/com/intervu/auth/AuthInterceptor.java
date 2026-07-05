package com.intervu.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    @Value("${intervu.jwt.secret:defaultSuperSecretKeyForIntervuMvpThatIsLongEnoughToUseForHMACSHA256}")
    private String jwtSecret;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip auth for OPTIONS requests (CORS preflight) and public endpoints
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || request.getRequestURI().startsWith("/api/health") || request.getRequestURI().startsWith("/api/auth")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header");
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing Authorization Header");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException("JWT does not contain a subject (userId)");
            }

            // Set userId in request attributes for controllers to use
            request.setAttribute("userId", userId);
            return true;

        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid Token");
            return false;
        }
    }
}
