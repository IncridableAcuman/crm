package com.savdocrm.modules.catalog.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank(message = "Category name is required.")
        @Size(max = 120, message = "Category name cannot exceed 120 characters.")
        String name,

        @Size(max = 4000, message = "Description cannot exceed 4000 characters.")
        String description,

        @Min(value = 0, message = "Sort order cannot be negative.")
        Integer sortOrder
) {
}
