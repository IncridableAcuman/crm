package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;

import java.util.UUID;

/**
 * Filter criteria for paginated product catalog search.
 */
public record ProductSearchCriteria(
        String search,
        String sku,
        String barcode,
        UUID categoryId,
        boolean includeSubcategories,
        UUID unitId,
        String brand,
        ProductStatus status,
        ProductType productType,
        Boolean isActive,
        boolean includeArchived
) {
}
