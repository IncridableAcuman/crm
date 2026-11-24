package com.savdocrm.modules.identity.infrastructure;

import com.savdocrm.modules.identity.application.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenCleanupTaskTest {

    @Test
    @DisplayName("Should execute scheduled cleanup with calculated 90-day retention cutoff")
    void shouldExecuteCleanupWithCalculatedCutoff() {
        AuthService authService = mock(AuthService.class);
        when(authService.cleanupExpiredRevokedTokens(any(Instant.class))).thenReturn(5);

        RefreshTokenCleanupTask task = new RefreshTokenCleanupTask(authService);
        ReflectionTestUtils.setField(task, "retentionDays", 90);

        Instant before = Instant.now().minusSeconds(86400L * 90);
        int purged = task.purgeExpiredRevokedTokens();
        Instant after = Instant.now().minusSeconds(86400L * 90);

        assertThat(purged).isEqualTo(5);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(authService).cleanupExpiredRevokedTokens(captor.capture());

        Instant cutoff = captor.getValue();
        assertThat(cutoff).isBetween(before.minusSeconds(2), after.plusSeconds(2));
    }
}
