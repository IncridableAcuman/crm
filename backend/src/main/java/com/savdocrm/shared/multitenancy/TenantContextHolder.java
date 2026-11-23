package com.savdocrm.shared.multitenancy;

import java.util.Optional;
import java.util.UUID;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setContext(TenantContext context) {
        if (context == null) {
            clear();
        } else {
            CONTEXT.set(context);
        }
    }

    public static Optional<TenantContext> getContext() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static UUID getRequiredTenantId() {
        TenantContext context = CONTEXT.get();
        if (context == null || context.tenantId() == null) {
            throw TenantContextMissingException.createDefault();
        }
        return context.tenantId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
