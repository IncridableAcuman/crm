package com.savdocrm.modules.identity.application.dto;

import com.savdocrm.modules.identity.domain.Permission;

import java.util.UUID;

public record PermissionDto(
        UUID id,
        String code,
        String module,
        String resource,
        String action,
        String description
) {
    public static PermissionDto fromEntity(Permission permission) {
        return new PermissionDto(
                permission.getId(),
                permission.getCode(),
                permission.getModule(),
                permission.getResource(),
                permission.getAction(),
                permission.getDescription()
        );
    }
}
