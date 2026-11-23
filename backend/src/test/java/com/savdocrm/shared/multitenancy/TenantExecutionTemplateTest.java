package com.savdocrm.shared.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantExecutionTemplateTest {

    private final TenantExecutionTemplate template = new TenantExecutionTemplate();

    @BeforeEach
    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("executeAsTenant runs supplier and cleans up ThreadLocal in finally")
    void shouldExecuteSupplierAndCleanup() {
        UUID targetTenant = UUID.randomUUID();

        String result = template.executeAsTenant(targetTenant, () -> {
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(targetTenant);
            return "SUCCESS";
        });

        assertThat(result).isEqualTo("SUCCESS");
        // Guarantees context is cleaned up after execution
        assertThat(TenantContextHolder.getContext()).isEmpty();
    }

    @Test
    @DisplayName("executeAsTenant cleans up context even when action throws exception")
    void shouldCleanupContextOnException() {
        UUID targetTenant = UUID.randomUUID();

        assertThatThrownBy(() -> template.executeAsTenant(targetTenant, () -> {
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(targetTenant);
            throw new IllegalStateException("Simulated failure inside tenant task");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Simulated failure inside tenant task");

        // Guarantees ThreadLocal is cleared despite failure
        assertThat(TenantContextHolder.getContext()).isEmpty();
    }

    @Test
    @DisplayName("executeAsTenant restores outer tenant context in nested execution")
    void shouldRestoreOuterTenantContextInNestedExecution() {
        UUID outerTenant = UUID.randomUUID();
        UUID innerTenant = UUID.randomUUID();

        template.executeAsTenant(outerTenant, () -> {
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(outerTenant);

            template.executeAsTenant(innerTenant, () -> {
                assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(innerTenant);
            });

            // Outer context is correctly restored
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(outerTenant);
        });

        // After outer completes, context is empty
        assertThat(TenantContextHolder.getContext()).isEmpty();
    }

    @Test
    @DisplayName("executeAsTenant runs runnable successfully")
    void shouldExecuteRunnable() {
        UUID tenantId = UUID.randomUUID();
        AtomicBoolean executed = new AtomicBoolean(false);

        template.executeAsTenant(tenantId, () -> {
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(tenantId);
            executed.set(true);
        });

        assertThat(executed.get()).isTrue();
        assertThat(TenantContextHolder.getContext()).isEmpty();
    }
}
