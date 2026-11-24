package com.savdocrm.modules.identity.application.dto;

import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;

import java.time.Instant;
import java.util.UUID;

public record UserRoleAssignmentDto(
        UUID id,
        UUID tenantId,
        UUID membershipId,
        UUID roleId,
        String roleCode,
        String roleName,
        RoleScopeType scopeType,
        UUID branchId,
        boolean isActive,
        Instant createdAt,
        Instant revokedAt
) {
    public static UserRoleAssignmentDto fromEntity(UserRoleAssignment assignment, String roleCode, String roleName) {
        return new UserRoleAssignmentDto(
                assignment.getId(),
                assignment.getTenantId(),
                assignment.getMembershipId(),
                assignment.getRoleId(),
                roleCode,
                roleName,
                assignment.getScopeType(),
                assignment.getBranchId(),
                assignment.isActive(),
                assignment.getCreatedAt(),
                assignment.getRevokedAt()
        );
    }
}
