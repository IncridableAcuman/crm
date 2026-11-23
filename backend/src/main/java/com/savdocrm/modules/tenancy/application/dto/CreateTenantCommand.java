package com.savdocrm.modules.tenancy.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantCommand(
        @NotBlank(message = "Tenant name is required")
        @Size(max = 150, message = "Tenant name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Tenant code is required")
        @Size(min = 2, max = 50, message = "Tenant code must be between 2 and 50 characters")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Tenant code must contain only lowercase letters, numbers, and hyphens")
        String code,

        @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
        String currencyCode,

        String timezone
) {
}
