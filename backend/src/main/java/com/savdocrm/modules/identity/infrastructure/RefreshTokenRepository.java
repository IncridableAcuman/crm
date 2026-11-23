package com.savdocrm.modules.identity.infrastructure;

import com.savdocrm.modules.identity.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByTokenFamily(UUID tokenFamily);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now, r.revokedReason = :reason WHERE r.tokenFamily = :tokenFamily")
    void revokeTokenFamily(@Param("tokenFamily") UUID tokenFamily, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now, r.revokedReason = :reason WHERE r.userId = :userId AND r.revoked = false")
    void revokeAllUserTokens(@Param("userId") UUID userId, @Param("now") Instant now, @Param("reason") String reason);
}
