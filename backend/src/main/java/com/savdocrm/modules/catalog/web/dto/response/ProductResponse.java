package com.savdocrm.modules.catalog.web.dto.response;

import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        String brand,
        ProductType productType,
        List<String> variantOptions,
        ProductStatus status,
        CategorySummaryResponse category,
        UnitSummaryResponse unit,
        List<ProductVariantResponse> variants,
        UUID activeDefaultVariantId,
        UUID historicalDefaultVariantId,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
