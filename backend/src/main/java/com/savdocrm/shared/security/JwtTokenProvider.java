package com.savdocrm.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateGlobalAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpirationSeconds());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("tokenType", "ACCESS")
                .issuer(jwtProperties.getIssuer())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateTenantAccessToken(UUID userId, String email, UUID tenantId, UUID membershipId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpirationSeconds());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("tenantId", tenantId.toString())
                .claim("membershipId", membershipId.toString())
                .claim("tokenType", "ACCESS")
                .issuer(jwtProperties.getIssuer())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRawRefreshToken() {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public Optional<Claims> parseAndValidateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .requireIssuer(jwtProperties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<UUID> extractUserId(Claims claims) {
        try {
            return Optional.ofNullable(claims.getSubject()).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<UUID> extractTenantId(Claims claims) {
        try {
            return Optional.ofNullable(claims.get("tenantId", String.class)).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Optional<UUID> extractMembershipId(Claims claims) {
        try {
            return Optional.ofNullable(claims.get("membershipId", String.class)).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getRefreshTokenExpirationSeconds() {
        return jwtProperties.getRefreshTokenExpirationSeconds();
    }
}
