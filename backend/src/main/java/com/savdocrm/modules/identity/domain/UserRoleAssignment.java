package com.savdocrm.modules.identity.domain;

import com.savdocrm.modules.identity.domain.exception.InvalidRoleScopeException;
import com.savdocrm.shared.domain.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_role_assignments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleAssignment extends BaseTenantEntity {

    @Column(name = "membership_id", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 30)
    private RoleScopeType scopeType;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @PrePersist
    @PreUpdate
    public void validateScopeInvariant() {
        if (scopeType == null) {
            throw new InvalidRoleScopeException("Scope type must not be null.");
        }
        if (scopeType == RoleScopeType.TENANT && branchId != null) {
            throw new InvalidRoleScopeException("TENANT scope assignment must have branchId == null.");
        }
        if (scopeType == RoleScopeType.BRANCH && branchId == null) {
            throw new InvalidRoleScopeException("BRANCH scope assignment requires a non-null branchId.");
        }
    }

    public void revoke() {
        this.isActive = false;
        this.revokedAt = Instant.now();
    }

    public boolean isGrantedForTenant() {
        return this.isActive && this.scopeType == RoleScopeType.TENANT;
    }

    public boolean isGrantedForBranch(UUID targetBranchId) {
        if (!this.isActive || targetBranchId == null) {
            return false;
        }
        if (this.scopeType == RoleScopeType.TENANT) {
            return true;
        }
        return this.scopeType == RoleScopeType.BRANCH && Objects.equals(this.branchId, targetBranchId);
    }
}
