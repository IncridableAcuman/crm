package com.savdocrm.modules.tenancy.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBranchCommand(
        @NotBlank(message = "Branch name is required")
        @Size(max = 150, message = "Branch name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Branch code is required")
        @Size(min = 2, max = 30, message = "Branch code must be between 2 and 30 characters")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Branch code must contain only uppercase letters, numbers, hyphens, and underscores")
        String code,

        String address,

        @Size(max = 30, message = "Phone cannot exceed 30 characters")
        String phone,

        boolean isMain
) {
}
