package com.savdocrm.modules.catalog.application.dto;

public record UpdateUnitCommand(
        String name,
        String symbol,
        boolean allowDecimal,
        int decimalPrecision
) {
}
