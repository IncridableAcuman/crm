package com.savdocrm.modules.catalog.web.dto.response;

import com.savdocrm.modules.catalog.domain.BarcodeType;

import java.time.Instant;
import java.util.UUID;

public record ProductBarcodeResponse(
        UUID id,
        UUID variantId,
        String barcode,
        BarcodeType barcodeType,
        boolean isPrimary,
        Instant createdAt,
        Instant updatedAt
) {
}
