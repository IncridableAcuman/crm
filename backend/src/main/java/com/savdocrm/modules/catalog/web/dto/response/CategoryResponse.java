package com.savdocrm.modules.catalog.web.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
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
}
