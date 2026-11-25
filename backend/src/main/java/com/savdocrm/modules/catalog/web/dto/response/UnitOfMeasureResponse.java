package com.savdocrm.modules.catalog.web.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UnitOfMeasureResponse(
        UUID id,
        String code,
        String name,
        String symbol,
        boolean allowDecimal,
        short decimalPrecision,
        boolean isSystem,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
