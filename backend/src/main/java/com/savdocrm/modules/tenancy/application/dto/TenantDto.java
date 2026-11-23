package com.savdocrm.modules.tenancy.application.dto;

import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantDto(
        UUID id,
        String name,
        String code,
        String currencyCode,
        String timezone,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantDto fromEntity(Tenant entity) {
        return new TenantDto(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getCurrencyCode(),
                entity.getTimezone(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
