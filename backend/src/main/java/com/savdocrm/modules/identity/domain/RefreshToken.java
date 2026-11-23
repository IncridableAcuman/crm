package com.savdocrm.modules.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "token_family", nullable = false)
    private UUID tokenFamily;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_ip", length = 50)
    private String createdIp;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "successor_token_hash", length = 64)
    private String successorTokenHash;

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public boolean isValid() {
        return !this.revoked && !isExpired();
    }

    public void revoke(String reason) {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.revokedReason = reason;
    }

    public void rotate(String successorHash) {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.revokedReason = "Rotated";
        this.successorTokenHash = successorHash;
    }
}
