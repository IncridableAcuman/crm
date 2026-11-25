package com.savdocrm.modules.catalog.web.dto.response;

import com.savdocrm.modules.catalog.domain.ProductStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record VariantLookupResponse(
        UUID variantId,
        UUID productId,
        String productName,
        String variantName,
        String sku,
        String primaryBarcode,
        Map<String, String> optionValues,
        UUID unitId,
        String unitCode,
        String unitSymbol,
        boolean allowDecimal,
        short decimalPrecision,
        BigDecimal referenceCostPrice,
        BigDecimal sellingPrice,
        ProductStatus productStatus,
        boolean variantActive,
        boolean activeDefault,
        boolean sellable
) {
}
