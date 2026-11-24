package com.savdocrm.modules.identity.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record RolePermissionUpdateRequest(
        @NotNull(message = "Permissions set must be provided (may be empty)")
        Set<String> permissions
) {}
