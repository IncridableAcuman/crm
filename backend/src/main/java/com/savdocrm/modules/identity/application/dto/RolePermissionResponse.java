package com.savdocrm.modules.identity.application.dto;

import java.util.Set;
import java.util.UUID;

public record RolePermissionResponse(
        UUID roleId,
        String roleCode,
        Set<String> permissions
) {}
