package com.savdocrm.modules.tenancy.domain.event;

import java.util.UUID;

/**
 * Domain event published synchronously within the tenant creation transaction.
 */
public record TenantCreatedEvent(UUID tenantId) {
}
