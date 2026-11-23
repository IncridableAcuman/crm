package com.savdocrm.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    private ClientIpResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver("127.0.0.1,::1,10.0.0.1");
        request = mock(HttpServletRequest.class);
    }

    @Test
    @DisplayName("Should extract client IP from X-Forwarded-For when connection is from configured trusted proxy")
    void shouldExtractClientIpFromTrustedProxy() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 198.51.100.1");

        String clientIp = resolver.resolveClientIp(request);

        assertThat(clientIp).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("Should ignore X-Forwarded-For and return remoteAddr when connection is from untrusted host")
    void shouldIgnoreForwardedHeadersFromUntrustedClient() {
        when(request.getRemoteAddr()).thenReturn("198.51.100.42");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.99"); // Spoofed header attempt

        String clientIp = resolver.resolveClientIp(request);

        assertThat(clientIp).isEqualTo("198.51.100.42");
    }

    @Test
    @DisplayName("Should return remoteAddr when connection is from trusted proxy but X-Forwarded-For is missing")
    void shouldFallbackToRemoteAddrWhenHeaderMissingOnTrustedProxy() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        String clientIp = resolver.resolveClientIp(request);

        assertThat(clientIp).isEqualTo("10.0.0.1");
    }
}
