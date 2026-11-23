package com.savdocrm.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class AuthCookieService {

    private final JwtProperties jwtProperties;
    private final List<String> allowedOrigins;

    public AuthCookieService(
            JwtProperties jwtProperties,
            @Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}") String allowedOriginsConfig) {
        this.jwtProperties = jwtProperties;
        this.allowedOrigins = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public ResponseCookie createRefreshTokenCookie(String rawRefreshToken) {
        return ResponseCookie.from(jwtProperties.getCookieName(), rawRefreshToken)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path(jwtProperties.getCookiePath())
                .maxAge(Duration.ofSeconds(jwtProperties.getRefreshTokenExpirationSeconds()))
                .sameSite(jwtProperties.getCookieSameSite())
                .build();
    }

    public ResponseCookie createCleanRefreshTokenCookie() {
        return ResponseCookie.from(jwtProperties.getCookieName(), "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .path(jwtProperties.getCookiePath())
                .maxAge(0)
                .sameSite(jwtProperties.getCookieSameSite())
                .build();
    }

    public Optional<String> extractRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> jwtProperties.getCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(val -> val != null && !val.isBlank())
                .findFirst();
    }

    /**
     * Validates that cross-origin cookie-authenticated requests originate from an allowed origin.
     * Protects cookie-authenticated endpoints against CSRF in both SameSite=Lax and SameSite=None topologies.
     */
    public void validateOriginForCookieAuth(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            boolean allowed = allowedOrigins.stream()
                    .anyMatch(orig -> orig.equalsIgnoreCase(origin.trim()));
            if (!allowed) {
                throw new AccessDeniedException("CSRF protection: Origin '" + origin + "' is not permitted.");
            }
        }
    }
}
