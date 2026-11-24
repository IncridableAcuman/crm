package com.savdocrm.modules.identity.application.dto;

import com.savdocrm.modules.identity.domain.RoleScopeType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRoleAssignmentRequest(
        @NotNull(message = "Membership ID is required")
        UUID membershipId,

        @NotNull(message = "Role ID is required")
        UUID roleId,

        @NotNull(message = "Scope type is required")
        RoleScopeType scopeType,

        UUID branchId
) {}
