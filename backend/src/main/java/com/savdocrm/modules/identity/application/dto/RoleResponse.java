package com.savdocrm.modules.identity.application.dto;

import com.savdocrm.modules.identity.domain.Role;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean isSystem,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt,
        Set<String> permissions
) {
    public static RoleResponse fromEntity(Role role, Set<String> permissions) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystem(),
                role.isActive(),
                role.getCreatedAt(),
                role.getUpdatedAt(),
                permissions != null ? permissions : Collections.emptySet()
        );
    }
}
