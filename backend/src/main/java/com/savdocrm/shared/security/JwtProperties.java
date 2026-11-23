package com.savdocrm.shared.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Secret key for signing tokens. Must be at least 256 bits (32 bytes) long.
     */
    private String secret = "v8y/B?E(H+MbQeThWmZq4t7w!z%C*F-J@NcRfUjXn2r5u8x/A?D(G+KbPdSgVkYp";

    /**
     * Dedicated secret key for token encryption.
     * Retained for backward-compatible environment binding; obsolete under strict single-use rotation.
     */
    private String encryptionSecret;

    /**
     * Access token validity in seconds. Default: 15 minutes (900 seconds).
     */
    private long accessTokenExpirationSeconds = 900;

    /**
     * Refresh token validity in seconds. Default: 30 days (2,592,000 seconds).
     */
    private long refreshTokenExpirationSeconds = 2592000;

    /**
     * Token issuer claim.
     */
    private String issuer = "savdocrm-auth";

    /**
     * Cookie name for refresh token.
     */
    private String cookieName = "savdocrm_refresh_token";

    /**
     * Cookie secure flag. In production, must be true.
     */
    private boolean cookieSecure = false;

    /**
     * Cookie SameSite attribute: Lax, Strict, or None.
     */
    private String cookieSameSite = "Lax";

    /**
     * Cookie path restriction.
     */
    private String cookiePath = "/api/v1/auth";
}
