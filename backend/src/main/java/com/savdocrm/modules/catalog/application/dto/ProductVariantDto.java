package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.ProductVariant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductVariantDto(
        UUID id,
        UUID productId,
        String sku,
        String name,
        Map<String, String> optionValues,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        boolean isDefault,
        boolean isActive,
        List<ProductBarcodeDto> barcodes,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static ProductVariantDto fromEntity(ProductVariant entity, List<ProductBarcodeDto> barcodes) {
        Map<String, String> safeOptions = entity.getOptionValues() == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(entity.getOptionValues()));
        List<ProductBarcodeDto> safeBarcodes = barcodes == null
                ? Collections.emptyList()
                : List.copyOf(barcodes);
        return new ProductVariantDto(
                entity.getId(),
                entity.getProductId(),
                entity.getSku(),
                entity.getName(),
                safeOptions,
                entity.getCostPrice(),
                entity.getSellingPrice(),
                entity.isDefault(),
                entity.isActive(),
                safeBarcodes,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
