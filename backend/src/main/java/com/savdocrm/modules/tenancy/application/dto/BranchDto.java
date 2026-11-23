package com.savdocrm.modules.tenancy.application.dto;

import com.savdocrm.modules.tenancy.domain.Branch;

import java.time.Instant;
import java.util.UUID;

public record BranchDto(
        UUID id,
        UUID tenantId,
        String name,
        String code,
        String address,
        String phone,
        boolean isActive,
        boolean isMain,
        Instant createdAt,
        Instant updatedAt
) {
    public static BranchDto fromEntity(Branch entity) {
        return new BranchDto(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getCode(),
                entity.getAddress(),
                entity.getPhone(),
                entity.isActive(),
                entity.isMain(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
