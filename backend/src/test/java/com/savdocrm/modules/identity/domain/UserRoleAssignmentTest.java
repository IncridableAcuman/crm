package com.savdocrm.modules.identity.domain;

import com.savdocrm.modules.identity.domain.exception.InvalidRoleScopeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleAssignmentTest {

    @Test
    @DisplayName("Scope invariant: TENANT scope requires branchId == null")
    void scopeInvariant_tenantScope_success() {
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();

        assignment.validateScopeInvariant();
        assertThat(assignment.isGrantedForTenant()).isTrue();
        assertThat(assignment.isGrantedForBranch(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("Scope invariant: TENANT scope with non-null branchId throws InvalidRoleScopeException")
    void scopeInvariant_tenantScopeWithBranch_throwsException() {
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .scopeType(RoleScopeType.TENANT)
                .branchId(UUID.randomUUID())
                .isActive(true)
                .build();

        assertThatThrownBy(assignment::validateScopeInvariant)
                .isInstanceOf(InvalidRoleScopeException.class)
                .hasMessageContaining("TENANT scope assignment must have branchId == null");
    }

    @Test
    @DisplayName("Scope invariant: BRANCH scope requires non-null branchId")
    void scopeInvariant_branchScope_success() {
        UUID branchId = UUID.randomUUID();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .scopeType(RoleScopeType.BRANCH)
                .branchId(branchId)
                .isActive(true)
                .build();

        assignment.validateScopeInvariant();
        assertThat(assignment.isGrantedForTenant()).isFalse();
        assertThat(assignment.isGrantedForBranch(branchId)).isTrue();
        assertThat(assignment.isGrantedForBranch(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Scope invariant: BRANCH scope with null branchId throws InvalidRoleScopeException")
    void scopeInvariant_branchScopeNullBranch_throwsException() {
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .scopeType(RoleScopeType.BRANCH)
                .branchId(null)
                .isActive(true)
                .build();

        assertThatThrownBy(assignment::validateScopeInvariant)
                .isInstanceOf(InvalidRoleScopeException.class)
                .hasMessageContaining("BRANCH scope assignment requires a non-null branchId");
    }

    @Test
    @DisplayName("Revoke assignment: sets isActive=false and revokedAt")
    void revoke_updatesStatusAndTimestamp() {
        UUID branchId = UUID.randomUUID();
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .scopeType(RoleScopeType.BRANCH)
                .branchId(branchId)
                .isActive(true)
                .build();

        assertThat(assignment.isActive()).isTrue();
        assertThat(assignment.getRevokedAt()).isNull();

        assignment.revoke();

        assertThat(assignment.isActive()).isFalse();
        assertThat(assignment.getRevokedAt()).isNotNull();
        assertThat(assignment.isGrantedForBranch(branchId)).isFalse();
        assertThat(assignment.isGrantedForTenant()).isFalse();
    }
}
