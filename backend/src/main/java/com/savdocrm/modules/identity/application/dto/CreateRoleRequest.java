package com.savdocrm.modules.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank(message = "Role code is required")
        @Pattern(regexp = "^[A-Z0-9_]{3,50}$", message = "Role code must consist of 3 to 50 uppercase alphanumeric characters or underscores")
        String code,

        @NotBlank(message = "Role name is required")
        @Size(max = 100, message = "Role name cannot exceed 100 characters")
        String name,

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        String description,

        Set<String> permissions
) {}
