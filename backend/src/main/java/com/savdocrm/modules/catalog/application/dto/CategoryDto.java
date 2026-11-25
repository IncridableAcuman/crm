package com.savdocrm.modules.catalog.application.dto;

import com.savdocrm.modules.catalog.domain.ProductCategory;

import java.time.Instant;
import java.util.UUID;

public record CategoryDto(
        UUID id,
        UUID parentId,
        String code,
        String name,
        String description,
        int depth,
        int sortOrder,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static CategoryDto fromEntity(ProductCategory entity) {
        return new CategoryDto(
                entity.getId(),
                entity.getParentId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getDepth(),
                entity.getSortOrder(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
