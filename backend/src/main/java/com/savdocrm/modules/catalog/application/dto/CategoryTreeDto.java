package com.savdocrm.modules.catalog.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Hierarchical category node projection built from a single flat tenant query.
 */
public record CategoryTreeDto(
        UUID id,
        UUID parentId,
        String code,
        String name,
        String description,
        int depth,
        int sortOrder,
        boolean isActive,
        List<CategoryTreeDto> children,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
