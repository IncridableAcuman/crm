package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.UnitOfMeasure;

import java.time.Instant;
import java.util.UUID;

public record UnitOfMeasureDto(
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
    public static UnitOfMeasureDto fromEntity(UnitOfMeasure entity) {
        return new UnitOfMeasureDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getSymbol(),
                entity.isAllowDecimal(),
                entity.getDecimalPrecision(),
                entity.isSystem(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
