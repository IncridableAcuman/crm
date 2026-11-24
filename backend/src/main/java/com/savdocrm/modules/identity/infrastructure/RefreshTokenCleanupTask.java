package com.savdocrm.modules.identity.infrastructure;

import com.savdocrm.modules.identity.application.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled background task for purging expired revoked refresh tokens
 * that exceed the configured retention window (default: 90 days).
 * Supported by composite database index (revoked, expires_at).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.token-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RefreshTokenCleanupTask {

    private final AuthService authService;

    @Value("${security.token-cleanup.retention-days:90}")
    private int retentionDays = 90;

    @Scheduled(cron = "${security.token-cleanup.cron:0 0 3 * * ?}")
    public int purgeExpiredRevokedTokens() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        log.info("Executing scheduled cleanup of expired revoked refresh tokens older than cutoff: {}", cutoff);
        int purged = authService.cleanupExpiredRevokedTokens(cutoff);
        log.info("Scheduled refresh token cleanup completed. Purged {} expired revoked tokens.", purged);
        return purged;
    }
}
