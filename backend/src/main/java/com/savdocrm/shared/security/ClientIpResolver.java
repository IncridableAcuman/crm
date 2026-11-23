package com.savdocrm.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves client IP address with strict verification against configured trusted reverse proxies.
 * Prevents IP spoofing via X-Forwarded-For headers from untrusted direct connections.
 */
@Slf4j
@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(
            @Value("${security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") String trustedProxiesConfig) {
        this.trustedProxies = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        if (remoteAddr == null || !isTrustedProxy(remoteAddr)) {
            // Direct client connection or untrusted intermediary: ignore forwarded headers
            return remoteAddr != null ? remoteAddr : "UNKNOWN";
        }

        // Remote peer is a verified trusted proxy: inspect X-Forwarded-For
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            String[] ips = xfHeader.split(",");
            if (ips.length > 0 && !ips[0].trim().isBlank()) {
                return ips[0].trim();
            }
        }

        return remoteAddr;
    }

    public boolean isTrustedProxy(String remoteAddr) {
        String normalized = remoteAddr.trim();
        return trustedProxies.contains(normalized);
    }
}
