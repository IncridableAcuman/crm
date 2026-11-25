package com.savdocrm.modules.catalog.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CreateVariantCommand(
        String sku,
        String name,
        Map<String, String> optionValues,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        boolean isDefault,
        List<BarcodeInput> barcodes
) {
}
