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

class TenantContextTaskDecoratorTest {

    private final TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();

    @BeforeEach
    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("TaskDecorator copies active context onto worker thread and cleans it up afterwards")
    void shouldPropagateContextToAsyncThreadAndCleanup() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.setContext(TenantContext.of(tenantId, "PARENT_THREAD"));

        AtomicReference<UUID> asyncTenantId = new AtomicReference<>();
        AtomicReference<UUID> postExecutionTenantId = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable decoratedTask = decorator.decorate(() -> {
            asyncTenantId.set(TenantContextHolder.getRequiredTenantId());
        });

        // Run decorated task on a separate thread pool worker thread
        Thread worker = new Thread(() -> {
            decoratedTask.run();
            // Check if ThreadLocal was cleaned up on worker thread
            TenantContextHolder.getContext().ifPresent(c -> postExecutionTenantId.set(c.tenantId()));
            latch.countDown();
        });

        worker.start();
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        // Worker thread successfully received parent context
        assertThat(asyncTenantId.get()).isEqualTo(tenantId);
        // Worker thread was cleaned up in finally block (zero thread pool contamination)
        assertThat(postExecutionTenantId.get()).isNull();
    }
}
