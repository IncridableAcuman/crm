package com.savdocrm.modules.catalog.application.dto;

import java.util.UUID;

public record UpdateProductCommand(
        UUID categoryId,
        UUID unitId,
        String name,
        String description,
        String brand
) {
}
