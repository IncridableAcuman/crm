package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.Product;
import com.savdocrm.modules.catalog.domain.ProductStatus;
import com.savdocrm.modules.catalog.domain.ProductType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductDto(
        UUID id,
        UUID categoryId,
        UUID unitId,
        String name,
        String description,
        String brand,
        ProductType productType,
        List<String> variantOptions,
        ProductStatus status,
        List<ProductVariantDto> variants,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static ProductDto fromEntity(Product entity, List<ProductVariantDto> variants) {
        return new ProductDto(
                entity.getId(),
                entity.getCategoryId(),
                entity.getUnitId(),
                entity.getName(),
                entity.getDescription(),
                entity.getBrand(),
                entity.getProductType(),
                entity.getVariantOptions() == null ? List.of() : List.copyOf(entity.getVariantOptions()),
                entity.getStatus(),
                variants == null ? List.of() : List.copyOf(variants),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
