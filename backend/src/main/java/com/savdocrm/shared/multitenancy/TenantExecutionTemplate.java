package com.savdocrm.shared.multitenancy;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class TenantExecutionTemplate {

    public <T> T executeAsTenant(UUID tenantId, Supplier<T> action) {
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null for executeAsTenant");
        Objects.requireNonNull(action, "Action cannot be null for executeAsTenant");

        Optional<TenantContext> previousContext = TenantContextHolder.getContext();
        try {
            TenantContextHolder.setContext(TenantContext.of(tenantId, "EXECUTION_TEMPLATE"));
            return action.get();
        } finally {
            if (previousContext.isPresent()) {
                TenantContextHolder.setContext(previousContext.get());
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    public void executeAsTenant(UUID tenantId, Runnable action) {
        Objects.requireNonNull(action, "Action cannot be null for executeAsTenant");
        executeAsTenant(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
