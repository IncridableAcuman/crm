package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.BarcodeType;
import com.savdocrm.modules.catalog.domain.ProductBarcode;

import java.time.Instant;
import java.util.UUID;

public record ProductBarcodeDto(
        UUID id,
        UUID variantId,
        String barcode,
        BarcodeType barcodeType,
        boolean isPrimary,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductBarcodeDto fromEntity(ProductBarcode entity) {
        return new ProductBarcodeDto(
                entity.getId(),
                entity.getVariantId(),
                entity.getBarcode(),
                entity.getBarcodeType(),
                entity.isPrimary(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
