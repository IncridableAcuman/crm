package com.savdocrm.modules.catalog.application.dto;

import java.util.UUID;

public record CreateCategoryCommand(
        UUID parentId,
        String code,
        String name,
        String description,
        Integer sortOrder
) {
}
