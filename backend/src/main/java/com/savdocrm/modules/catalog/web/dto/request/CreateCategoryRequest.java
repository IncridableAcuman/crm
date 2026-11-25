package com.savdocrm.modules.catalog.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(
        UUID parentId,

        @NotBlank(message = "Category code is required.")
        @Size(min = 2, max = 50, message = "Category code must be between 2 and 50 characters.")
        @Pattern(
                regexp = "^(?i)\\s*[A-Z0-9_-]{2,50}\\s*$",
                message = "Category code may only contain letters, digits, '_', and '-'."
        )
        String code,

        @NotBlank(message = "Category name is required.")
        @Size(max = 120, message = "Category name cannot exceed 120 characters.")
        String name,

        @Size(max = 4000, message = "Description cannot exceed 4000 characters.")
        String description,

        @Min(value = 0, message = "Sort order cannot be negative.")
        Integer sortOrder
) {
}
