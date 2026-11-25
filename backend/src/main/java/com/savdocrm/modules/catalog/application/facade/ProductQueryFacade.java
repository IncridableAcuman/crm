package com.savdocrm.modules.catalog.application.facade;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public read-only query contract for downstream bounded contexts (Inventory, Sales, Purchases).
 * Resolves tenant scope strictly from TenantContextHolder.
 */
public interface ProductQueryFacade {

    Optional<VariantCatalogDto> findVariantById(UUID variantId);

    List<VariantCatalogDto> findVariantsByIds(Set<UUID> variantIds);

    Optional<VariantCatalogDto> findActiveVariantByBarcode(String barcode);

    Optional<VariantCatalogDto> findActiveVariantBySku(String sku);
}
