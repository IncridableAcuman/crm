package com.savdocrm.modules.catalog.application.dto;

public record CreateUnitCommand(
        String code,
        String name,
        String symbol,
        boolean allowDecimal,
        int decimalPrecision
) {
}
