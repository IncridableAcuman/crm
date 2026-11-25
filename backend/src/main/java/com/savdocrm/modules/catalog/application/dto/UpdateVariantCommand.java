package com.savdocrm.modules.catalog.application.dto;

import java.math.BigDecimal;
import java.util.Map;

public record UpdateVariantCommand(
        String sku,
        String name,
        Map<String, String> optionValues,
        BigDecimal costPrice,
        BigDecimal sellingPrice
) {
}
