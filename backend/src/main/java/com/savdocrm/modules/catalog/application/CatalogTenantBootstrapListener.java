package com.savdocrm.modules.catalog.application;

import com.savdocrm.modules.tenancy.domain.event.TenantCreatedEvent;
import com.savdocrm.shared.multitenancy.TenantExecutionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Initializes default catalog units of measure synchronously inside the tenant creation transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogTenantBootstrapListener {

    private final TenantExecutionTemplate tenantExecutionTemplate;
    private final UnitOfMeasureService unitOfMeasureService;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        if (event == null || event.tenantId() == null) {
            return;
        }
        log.debug("Seeding standard units of measure for newly created tenant {}", event.tenantId());
        tenantExecutionTemplate.executeAsTenant(
                event.tenantId(),
                unitOfMeasureService::initializeStandardUnitsForCurrentTenant
        );
    }
}
