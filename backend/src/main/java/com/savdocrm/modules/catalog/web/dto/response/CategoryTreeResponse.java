package com.savdocrm.modules.catalog.web.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CategoryTreeResponse(
        UUID id,
        UUID parentId,
        String code,
        String name,
        String description,
        int depth,
        int sortOrder,
        boolean isActive,
        List<CategoryTreeResponse> children,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
