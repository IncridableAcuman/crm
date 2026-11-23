package com.savdocrm.shared.multitenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextHolderTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Fail-Closed: Throws TenantContextMissingException when no context is set")
    void shouldThrowWhenNoContextIsSet() {
        assertThatThrownBy(TenantContextHolder::getRequiredTenantId)
                .isInstanceOf(TenantContextMissingException.class)
                .hasMessageContaining("Security Violation: Operation invoked without a valid, trusted tenant context.");
    }

    @Test
    @DisplayName("Context set and get works on the same thread")
    void shouldSetAndGetContext() {
        UUID tenantId = UUID.randomUUID();
        TenantContext context = TenantContext.of(tenantId, "TEST");

        TenantContextHolder.setContext(context);

        assertThat(TenantContextHolder.getContext()).isPresent();
        assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("ThreadLocal cleanup removes context completely")
    void shouldClearContext() {
        TenantContextHolder.setContext(TenantContext.of(UUID.randomUUID()));
        assertThat(TenantContextHolder.getContext()).isPresent();

        TenantContextHolder.clear();

        assertThat(TenantContextHolder.getContext()).isEmpty();
        assertThatThrownBy(TenantContextHolder::getRequiredTenantId)
                .isInstanceOf(TenantContextMissingException.class);
    }

    @Test
    @DisplayName("Thread Isolation: Tenant context in Thread A is not visible in Thread B")
    void shouldIsolateContextBetweenThreads() throws InterruptedException {
        UUID tenantA = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.of(tenantA));

        AtomicReference<UUID> threadBContext = new AtomicReference<>();
        AtomicReference<Throwable> threadBError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread threadB = new Thread(() -> {
            try {
                threadBContext.set(TenantContextHolder.getRequiredTenantId());
            } catch (Throwable t) {
                threadBError.set(t);
            } finally {
                latch.countDown();
            }
        });

        threadB.start();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        // Thread B must fail closed because it has no context
        assertThat(threadBContext.get()).isNull();
        assertThat(threadBError.get()).isInstanceOf(TenantContextMissingException.class);

        // Thread A still retains Tenant A
        assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(tenantA);
    }
}
