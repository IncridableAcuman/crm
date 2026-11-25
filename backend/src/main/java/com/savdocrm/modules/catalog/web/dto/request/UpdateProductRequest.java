package com.savdocrm.modules.catalog.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateProductRequest(
        UUID categoryId,

        @NotNull(message = "Unit of measure ID is required.")
        UUID unitId,

        @NotBlank(message = "Product name is required.")
        @Size(max = 200, message = "Product name cannot exceed 200 characters.")
        String name,

        @Size(max = 4000, message = "Description cannot exceed 4000 characters.")
        String description,

        @Size(max = 100, message = "Brand cannot exceed 100 characters.")
        String brand
) {
}
