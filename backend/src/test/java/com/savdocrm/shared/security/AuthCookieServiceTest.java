package com.savdocrm.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthCookieServiceTest {

    @Test
    @DisplayName("Should strictly forbid wildcard '*' in allowed origins configuration when credentials are used")
    void shouldRejectWildcardOriginInConfiguration() {
        JwtProperties jwtProps = new JwtProperties();
        assertThatThrownBy(() -> new AuthCookieService(jwtProps, "http://localhost:5173,*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSRF security violation: Wildcard '*' origin is strictly forbidden");
    }

    @Test
    @DisplayName("Should create secure HttpOnly refresh cookie with configured properties")
    void shouldCreateConfiguredRefreshTokenCookie() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setCookieName("savdocrm_refresh_token");
        jwtProps.setCookiePath("/api/v1/auth");
        jwtProps.setCookieSameSite("Lax");
        jwtProps.setCookieSecure(true);
        jwtProps.setRefreshTokenExpirationSeconds(2592000);

        AuthCookieService service = new AuthCookieService(jwtProps, "https://app.savdocrm.com");
        ResponseCookie cookie = service.createRefreshTokenCookie("raw-token-value");

        assertThat(cookie.getName()).isEqualTo("savdocrm_refresh_token");
        assertThat(cookie.getValue()).isEqualTo("raw-token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(2592000);
    }

    @Test
    @DisplayName("Should create clean cookie with maxAge=0 for logout")
    void shouldCreateCleanCookieOnLogout() {
        JwtProperties jwtProps = new JwtProperties();
        AuthCookieService service = new AuthCookieService(jwtProps, "https://app.savdocrm.com");
        ResponseCookie clean = service.createCleanRefreshTokenCookie();

        assertThat(clean.getValue()).isEmpty();
        assertThat(clean.getMaxAge().getSeconds()).isEqualTo(0);
        assertThat(clean.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("Should extract refresh token from cookies when present")
    void shouldExtractRefreshTokenFromCookies() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setCookieName("savdocrm_refresh_token");
        AuthCookieService service = new AuthCookieService(jwtProps, "https://app.savdocrm.com");

        HttpServletRequest request = mock(HttpServletRequest.class);
        Cookie[] cookies = new Cookie[]{
                new Cookie("other_cookie", "123"),
                new Cookie("savdocrm_refresh_token", "secret-token")
        };
        when(request.getCookies()).thenReturn(cookies);

        Optional<String> extracted = service.extractRefreshTokenFromCookies(request);
        assertThat(extracted).isPresent().contains("secret-token");
    }

    @Test
    @DisplayName("Should reject Origin null with AccessDeniedException")
    void shouldRejectOriginNull() {
        JwtProperties jwtProps = new JwtProperties();
        AuthCookieService service = new AuthCookieService(jwtProps, "https://app.savdocrm.com");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn("null");

        assertThatThrownBy(() -> service.validateOriginForCookieAuth(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Origin 'null' is strictly disallowed");
    }
}
