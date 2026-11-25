package com.savdocrm.modules.catalog.web.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        UUID productId,
        String sku,
        String name,
        Map<String, String> optionValues,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        boolean isDefault,
        boolean isActiveDefault,
        boolean isActive,
        List<ProductBarcodeResponse> barcodes,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
