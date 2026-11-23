package com.savdocrm.modules.identity.web;

import com.savdocrm.modules.identity.application.AuthService;
import com.savdocrm.modules.identity.application.dto.AuthResponse;
import com.savdocrm.modules.identity.application.dto.LoginRequest;
import com.savdocrm.modules.identity.application.dto.RefreshTokenRequest;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.application.dto.SelectTenantRequest;
import com.savdocrm.shared.dto.ApiResponse;
import com.savdocrm.shared.security.AuthCookieService;
import com.savdocrm.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, registration, session, and tenant selection endpoints")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account with optional initial store")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authService.register(request, ipAddress, userAgent);
        String rawRefreshToken = response.getRefreshToken();
        response.setRefreshToken(null); // Never expose raw refresh token in JSON response body

        ResponseCookie cookie = authCookieService.createRefreshTokenCookie(rawRefreshToken);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(response, "Account registered successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user credentials and receive global token with available tenants")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authService.login(request, ipAddress, userAgent);
        String rawRefreshToken = response.getRefreshToken();
        response.setRefreshToken(null); // Never expose raw refresh token in JSON response body

        ResponseCookie cookie = authCookieService.createRefreshTokenCookie(rawRefreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/select-tenant")
    @Operation(summary = "Select active tenant store and receive tenant-scoped access token",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AuthResponse>> selectTenant(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SelectTenantRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AuthResponse response = authService.selectTenant(principal.getId(), request, ipAddress, userAgent);
        String rawRefreshToken = response.getRefreshToken();
        response.setRefreshToken(null);

        ResponseCookie cookie = authCookieService.createRefreshTokenCookie(rawRefreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(response, "Tenant selected successfully"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and obtain new access token via HttpOnly cookie or payload")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        authCookieService.validateOriginForCookieAuth(httpRequest);
        String ipAddress = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        // Prefer extracting token from secure HttpOnly cookie; fallback to request payload
        String rawToken = authCookieService.extractRefreshTokenFromCookies(httpRequest)
                .orElseGet(() -> request != null ? request.getRefreshToken() : null);

        AuthResponse response = authService.refreshToken(rawToken, ipAddress, userAgent);
        String newRawRefreshToken = response.getRefreshToken();
        response.setRefreshToken(null);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (newRawRefreshToken != null) {
            ResponseCookie cookie = authCookieService.createRefreshTokenCookie(newRawRefreshToken);
            builder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return builder.body(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out user, revoke refresh token session, and clear HttpOnly cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        authCookieService.validateOriginForCookieAuth(httpRequest);
        String rawToken = authCookieService.extractRefreshTokenFromCookies(httpRequest)
                .orElseGet(() -> request != null ? request.getRefreshToken() : null);

        if (rawToken != null && !rawToken.isBlank()) {
            authService.logout(rawToken);
        }

        ResponseCookie cleanCookie = authCookieService.createCleanRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponse.success(null, "Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile and membership info",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<AuthResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        AuthResponse response = authService.getCurrentUser(principal.getId(), principal.getActiveTenantId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
