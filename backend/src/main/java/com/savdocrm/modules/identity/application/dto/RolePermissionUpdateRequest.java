package com.savdocrm.modules.identity.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public record RolePermissionUpdateRequest(
        @NotNull(message = "Permissions list must be provided (may be empty)")
        List<String> permissions
) {
    public RolePermissionUpdateRequest(Set<String> permissions) {
        this(permissions != null ? new ArrayList<>(permissions) : Collections.emptyList());
    }
}
