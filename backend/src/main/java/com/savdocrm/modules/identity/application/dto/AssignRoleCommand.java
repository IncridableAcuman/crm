package com.savdocrm.modules.identity.application.dto;

import com.savdocrm.modules.identity.domain.RoleScopeType;

import java.util.UUID;

public record AssignRoleCommand(
        UUID tenantId,
        UUID membershipId,
        UUID roleId,
        RoleScopeType scopeType,
        UUID branchId
) {}
