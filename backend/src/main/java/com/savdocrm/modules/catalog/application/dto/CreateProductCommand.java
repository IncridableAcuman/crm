package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;

import java.util.List;
import java.util.UUID;

public record CreateProductCommand(
        UUID categoryId,
        UUID unitId,
        String name,
        String description,
        String brand,
        ProductType productType,
        List<String> variantOptions,
        ProductStatus status,
        List<CreateVariantCommand> variants
) {
}
