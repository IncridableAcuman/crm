package com.savdocrm.modules.catalog.web.dto.response;

import java.util.UUID;

public record UnitSummaryResponse(
        UUID id,
        String code,
        String name,
        String symbol,
        boolean allowDecimal,
        short decimalPrecision
) {
}
