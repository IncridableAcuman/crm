package com.savdocrm.modules.catalog.application.dto;

public record UpdateCategoryCommand(
        String name,
        String description,
        Integer sortOrder
) {
}
