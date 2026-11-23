package com.savdocrm.shared.multitenancy;

import java.util.Objects;
import java.util.UUID;

public record TenantContext(UUID tenantId, String executionSource) {

    public TenantContext {
        Objects.requireNonNull(tenantId, "Tenant ID cannot be null in TenantContext");
        if (executionSource == null || executionSource.isBlank()) {
            executionSource = "SYSTEM";
        }
    }

    public static TenantContext of(UUID tenantId) {
        return new TenantContext(tenantId, "REQUEST");
    }

    public static TenantContext of(UUID tenantId, String executionSource) {
        return new TenantContext(tenantId, executionSource);
    }
}
