package com.savdocrm.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
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
                .map(this::normalizeOrigin)
                .toList();

        if (this.allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("CSRF security violation: Wildcard '*' origin is strictly forbidden when cookie credentials are used.");
        }
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
     * Validates Origin and Referer headers on cookie-authenticated endpoints (/refresh, /logout).
     * Enforces strict defense-in-depth against CSRF:
     * 1. If Origin is provided, it must match an allowed origin and must not be 'null'.
     * 2. If Origin is missing, extracts origin from Referer header and validates against allowed origins.
     * 3. If both Origin and Referer are missing on a request bearing ambient cookie credentials,
     *    the request is rejected with 403 Forbidden.
     */
    public void validateOriginForCookieAuth(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        boolean hasAuthCookie = extractRefreshTokenFromCookies(request).isPresent();

        // 1. Validate Origin header if present
        if (origin != null && !origin.isBlank()) {
            String trimmedOrigin = origin.trim();
            if ("null".equalsIgnoreCase(trimmedOrigin)) {
                log.warn("CSRF rejected: 'Origin: null' received from IP {}", request.getRemoteAddr());
                throw new AccessDeniedException("CSRF protection: Origin 'null' is strictly disallowed.");
            }

            if (!isAllowed(trimmedOrigin)) {
                log.warn("CSRF rejected: Unauthorized Origin '{}' from IP {}", trimmedOrigin, request.getRemoteAddr());
                throw new AccessDeniedException("CSRF protection: Origin '" + trimmedOrigin + "' is not permitted.");
            }
            return;
        }

        // 2. Fallback to Referer header if Origin is absent
        if (referer != null && !referer.isBlank()) {
            String refererOrigin = extractOriginFromReferer(referer.trim());
            if (refererOrigin == null || !isAllowed(refererOrigin)) {
                log.warn("CSRF rejected: Unauthorized Referer '{}' (extracted origin: '{}') from IP {}", referer, refererOrigin, request.getRemoteAddr());
                throw new AccessDeniedException("CSRF protection: Referer '" + referer + "' does not match permitted origins.");
            }
            return;
        }

        // 3. Both Origin and Referer are absent
        if (hasAuthCookie) {
            log.warn("CSRF rejected: Cookie-bearing request to sensitive endpoint without Origin or Referer header from IP {}", request.getRemoteAddr());
            throw new AccessDeniedException("CSRF protection: Missing Origin and Referer headers on cookie-authenticated request.");
        }
    }

    private boolean isAllowed(String candidateOrigin) {
        String normalized = normalizeOrigin(candidateOrigin);
        return allowedOrigins.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(normalized));
    }

    private String normalizeOrigin(String origin) {
        if (origin == null) return "";
        String s = origin.trim();
        if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private String extractOriginFromReferer(String referer) {
        try {
            URI uri = URI.create(referer);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);

            if (port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                return scheme + "://" + host;
            }
            return scheme + "://" + host + ":" + port;
        } catch (Exception e) {
            return null;
        }
    }
}
