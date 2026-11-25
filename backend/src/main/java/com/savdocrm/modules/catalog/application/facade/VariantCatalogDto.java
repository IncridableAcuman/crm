package com.savdocrm.modules.catalog.application.facade;

import com.savdocrm.modules.catalog.domain.ProductStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable catalog projection exposed across module boundaries (Inventory, Sales, Purchases).
 */
public record VariantCatalogDto(
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
        boolean sellable
) {
}
