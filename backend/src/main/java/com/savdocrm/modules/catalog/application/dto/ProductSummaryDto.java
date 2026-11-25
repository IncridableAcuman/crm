package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight paginated summary projection for catalog product listing.
 */
public record ProductSummaryDto(
        UUID id,
        String name,
        String brand,
        ProductType productType,
        ProductStatus status,
        UUID categoryId,
        String categoryName,
        UUID unitId,
        String unitCode,
        String unitSymbol,
        int variantCount,
        String defaultSku,
        String primaryBarcode,
        BigDecimal minSellingPrice,
        BigDecimal maxSellingPrice,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
