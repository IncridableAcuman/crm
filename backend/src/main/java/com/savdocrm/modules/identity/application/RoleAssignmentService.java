package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.application.dto.AssignRoleCommand;
import com.savdocrm.modules.identity.application.dto.UserRoleAssignmentDto;
import com.savdocrm.modules.identity.domain.Permissions;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.domain.exception.CrossTenantAssignmentException;
import com.savdocrm.modules.identity.domain.exception.DuplicateRoleAssignmentException;
import com.savdocrm.modules.identity.domain.exception.InactiveMembershipException;
import com.savdocrm.modules.identity.domain.exception.InvalidRoleScopeException;
import com.savdocrm.modules.identity.domain.exception.LastActiveOwnerException;
import com.savdocrm.modules.identity.domain.exception.PermissionDeniedException;
import com.savdocrm.modules.identity.domain.exception.RoleNotFoundException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RolePermissionRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Branch;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for role assignment, revocation, privilege containment enforcement,
 * and concurrency-safe last active owner protection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleAssignmentService {

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final PermissionAuthorizationService permissionAuthorizationService;

    /**
     * Assigns a role to a tenant membership with explicit scope parameters.
     * Enforces Grant-What-You-Hold, Owner Protection Guard, Self-Elevation prevention,
     * and composite tenant isolation.
     */
    @Transactional
    public UserRoleAssignmentDto assignRole(AssignRoleCommand command, UUID actorUserId) {
        log.info("Assigning role {} to membership {} in tenant {} by actor {}",
                command.roleId(), command.membershipId(), command.tenantId(), actorUserId);

        // 1. Validate Scope Constraints
        if (command.scopeType() == null) {
            throw new InvalidRoleScopeException("Scope type must be specified.");
        }
        if (command.scopeType() == RoleScopeType.TENANT && command.branchId() != null) {
            throw new InvalidRoleScopeException("TENANT scope assignment must not specify a branchId.");
        }
        if (command.scopeType() == RoleScopeType.BRANCH && command.branchId() == null) {
            throw new InvalidRoleScopeException("BRANCH scope assignment requires a non-null branchId.");
        }

        // 2. Validate Tenant Status
        Tenant tenant = tenantRepository.findById(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + command.tenantId()));
        if (!tenant.isActive()) {
            throw new IllegalStateException("Cannot assign roles in an inactive or suspended tenant.");
        }

        // 3. Validate Target Membership and User
        TenantMembership targetMembership = tenantMembershipRepository.findById(command.membershipId())
                .orElseThrow(() -> new InactiveMembershipException("Target membership not found with ID: " + command.membershipId()));

        if (!targetMembership.getTenantId().equals(command.tenantId())) {
            throw new CrossTenantAssignmentException("Target membership does not belong to tenant: " + command.tenantId());
        }
        if (!targetMembership.isActive()) {
            throw new InactiveMembershipException("Cannot assign roles to a suspended or inactive membership.");
        }

        User targetUser = userRepository.findById(targetMembership.getUserId())
                .orElseThrow(() -> new IllegalStateException("Underlying user not found for membership: " + targetMembership.getId()));
        if (!targetUser.isActive()) {
            throw new InactiveMembershipException("Cannot assign roles to a globally suspended or inactive user.");
        }

        // 4. Validate Target Role
        Role role = roleRepository.findById(command.roleId())
                .orElseThrow(() -> new RoleNotFoundException(command.roleId()));

        if (!role.getTenantId().equals(command.tenantId())) {
            throw new CrossTenantAssignmentException("Role does not belong to tenant: " + command.tenantId());
        }
        if (!role.isActive()) {
            throw new IllegalArgumentException("Cannot assign an inactive role: " + role.getCode());
        }

        // When assigning OWNER, coordinate with global user suspension via canonical lock order (User -> Tenant)
        if (role.isOwner()) {
            User lockedUser = userRepository.findByIdWithLock(targetMembership.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Underlying user not found: " + targetMembership.getUserId()));
            if (!lockedUser.isActive()) {
                throw new InactiveMembershipException("Cannot assign roles to a globally suspended or inactive user.");
            }

            Tenant lockedTenant = tenantRepository.findByIdWithLock(command.tenantId())
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + command.tenantId()));
            if (!lockedTenant.isActive()) {
                throw new IllegalStateException("Cannot assign roles in an inactive or suspended tenant.");
            }
        }

        // 5. Validate Branch if Branch-Scoped
        if (command.scopeType() == RoleScopeType.BRANCH) {
            Branch branch = branchRepository.findByTenantIdAndId(command.tenantId(), command.branchId())
                    .orElseThrow(() -> new CrossTenantAssignmentException("Target branch not found or does not belong to tenant: " + command.branchId()));
            if (!branch.isActive()) {
                throw new IllegalArgumentException("Cannot assign roles for an inactive branch: " + branch.getCode());
            }
        }

        // 6. Enforce Actor Authorization, Privilege Containment & Self-Elevation
        enforceAssignmentPrivileges(command.tenantId(), actorUserId, targetMembership.getUserId(), role, command.scopeType());

        // 7. Check for duplicate active assignment
        checkForDuplicateActiveAssignment(command);

        // 8. Create and persist UserRoleAssignment
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .membershipId(command.membershipId())
                .roleId(command.roleId())
                .scopeType(command.scopeType())
                .branchId(command.branchId())
                .isActive(true)
                .build();
        assignment.setTenantId(command.tenantId());
        assignment.setCreatedBy(actorUserId);
        assignment.setUpdatedBy(actorUserId);

        try {
            UserRoleAssignment saved = userRoleAssignmentRepository.save(assignment);
            log.info("Successfully created assignment {} for role {} on membership {}",
                    saved.getId(), role.getCode(), targetMembership.getId());
            return UserRoleAssignmentDto.fromEntity(saved, role.getCode(), role.getName());
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateRoleAssignmentException("An active role assignment with matching scope already exists.");
        }
    }

    /**
     * Revokes an existing role assignment. Preserves historical assignment record for audit trails.
     * Enforces concurrency-safe Last Active Owner check via pessimistic parent tenant lock.
     */
    @Transactional
    public void revokeRoleAssignment(UUID tenantId, UUID assignmentId, UUID actorUserId) {
        log.info("Revoking role assignment {} in tenant {} by actor {}", assignmentId, tenantId, actorUserId);

        UserRoleAssignment assignment = userRoleAssignmentRepository.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Role assignment not found with ID: " + assignmentId));

        if (!assignment.isActive()) {
            log.info("Assignment {} is already revoked. Idempotent return.", assignmentId);
            return;
        }

        Role role = roleRepository.findById(assignment.getRoleId())
                .orElseThrow(() -> new RoleNotFoundException(assignment.getRoleId()));

        // Actor Authorization Check
        TenantMembership actorMembership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, actorUserId)
                .orElseThrow(() -> new PermissionDeniedException("Actor is not a member of tenant: " + tenantId));
        if (!actorMembership.isActive()) {
            throw new PermissionDeniedException("Actor membership is not ACTIVE.");
        }

        if (role.isOwner()) {
            // Owner Protection Guard: Only an active OWNER can revoke an OWNER assignment
            boolean actorIsOwner = permissionAuthorizationService.hasPermission(tenantId, actorMembership.getId(), Permissions.IDENTITY_ROLES_MANAGE)
                    && isMemberActiveOwner(tenantId, actorMembership.getId());
            if (!actorIsOwner) {
                throw new PermissionDeniedException("Only an eligible active OWNER can revoke the OWNER role.");
            }

            // Pessimistic parent tenant row locking to serialize owner count verification
            tenantRepository.findByIdWithLock(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));

            // Re-evaluate eligible OWNER count excluding this assignment
            long remainingOwners = userRoleAssignmentRepository.countEligibleOwners(tenantId, null, assignmentId, null);
            if (remainingOwners == 0) {
                log.warn("Attempt to revoke final active OWNER assignment {} in tenant {} rejected.", assignmentId, tenantId);
                throw new LastActiveOwnerException(tenantId);
            }
        } else {
            // Non-owner role revocation requires identity.roles.manage or OWNER
            boolean canManageRoles = permissionAuthorizationService.hasPermission(tenantId, actorMembership.getId(), Permissions.IDENTITY_ROLES_MANAGE);
            if (!canManageRoles) {
                throw new PermissionDeniedException("Actor lacks permission 'identity.roles.manage' to revoke roles.");
            }
        }

        // Soft revocation preserving audit history
        assignment.revoke();
        assignment.setUpdatedBy(actorUserId);
        userRoleAssignmentRepository.save(assignment);
        log.info("Successfully revoked assignment {} (marked inactive, audit lineage intact).", assignmentId);
    }

    /**
     * Checks whether an actor is authorized to assign a specific role in a tenant.
     */
    @Transactional(readOnly = true)
    public boolean canAssignRole(UUID tenantId, UUID actorUserId, UUID roleId) {
        if (tenantId == null || actorUserId == null || roleId == null) {
            return false;
        }

        TenantMembership actorMembership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, actorUserId).orElse(null);
        if (actorMembership == null || !actorMembership.isActive()) {
            return false;
        }

        Role role = roleRepository.findByTenantIdAndId(tenantId, roleId).orElse(null);
        if (role == null || !role.isActive()) {
            return false;
        }

        if (role.isOwner()) {
            return isMemberActiveOwner(tenantId, actorMembership.getId());
        }

        // Actor must hold identity.roles.manage
        if (!permissionAuthorizationService.hasPermission(tenantId, actorMembership.getId(), Permissions.IDENTITY_ROLES_MANAGE)) {
            return false;
        }

        // Containment check: actor must hold all permissions granted by role
        Set<String> rolePermissions = rolePermissionRepository.findPermissionCodesByTenantIdAndRoleId(tenantId, role.getId());
        Set<String> actorPermissions = permissionAuthorizationService.getEffectiveTenantPermissions(tenantId, actorMembership.getId());
        return actorPermissions.containsAll(rolePermissions);
    }

    private void enforceAssignmentPrivileges(UUID tenantId, UUID actorUserId, UUID targetUserId, Role role, RoleScopeType scopeType) {
        if (actorUserId == null) {
            throw new PermissionDeniedException("Unauthenticated actors cannot assign roles.");
        }

        // Prevent Self-Elevation / Self-Assignment
        if (Objects.equals(actorUserId, targetUserId)) {
            throw new PermissionDeniedException("Self-assignment or self-elevation is strictly prohibited.");
        }

        TenantMembership actorMembership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, actorUserId)
                .orElseThrow(() -> new PermissionDeniedException("Actor is not a member of tenant: " + tenantId));
        if (!actorMembership.isActive()) {
            throw new PermissionDeniedException("Actor membership is not ACTIVE.");
        }

        boolean actorIsOwner = isMemberActiveOwner(tenantId, actorMembership.getId());

        if (role.isOwner()) {
            // Owner Protection Guard
            if (!actorIsOwner) {
                throw new PermissionDeniedException("Only an eligible active OWNER can assign the OWNER role.");
            }
            if (scopeType != RoleScopeType.TENANT) {
                throw new InvalidRoleScopeException("The OWNER role can only be assigned at store-wide TENANT scope.");
            }
            return;
        }

        // If actor is OWNER, they hold all permissions and can assign any non-owner role
        if (actorIsOwner) {
            return;
        }

        // Non-owner actor must hold identity.roles.manage
        if (!permissionAuthorizationService.hasPermission(tenantId, actorMembership.getId(), Permissions.IDENTITY_ROLES_MANAGE)) {
            throw new PermissionDeniedException("Actor lacks permission 'identity.roles.manage' to assign roles.");
        }

        // The Containment Rule: Actor can only grant permissions they themselves actively possess
        Set<String> rolePermissions = rolePermissionRepository.findPermissionCodesByTenantIdAndRoleId(tenantId, role.getId());
        Set<String> actorPermissions = permissionAuthorizationService.getEffectiveTenantPermissions(tenantId, actorMembership.getId());

        if (!actorPermissions.containsAll(rolePermissions)) {
            Set<String> missingPermissions = rolePermissions.stream()
                    .filter(p -> !actorPermissions.contains(p))
                    .collect(java.util.stream.Collectors.toSet());
            throw new PermissionDeniedException("Containment violation: Actor lacks permissions granted by this role: " + missingPermissions);
        }
    }

    private void checkForDuplicateActiveAssignment(AssignRoleCommand command) {
        boolean duplicate;
        if (command.scopeType() == RoleScopeType.TENANT) {
            duplicate = userRoleAssignmentRepository.findByTenantIdAndMembershipIdAndRoleIdAndScopeTypeAndIsActiveTrue(
                    command.tenantId(), command.membershipId(), command.roleId(), RoleScopeType.TENANT).isPresent();
        } else {
            duplicate = userRoleAssignmentRepository.findByTenantIdAndMembershipIdAndRoleIdAndScopeTypeAndBranchIdAndIsActiveTrue(
                    command.tenantId(), command.membershipId(), command.roleId(), RoleScopeType.BRANCH, command.branchId()).isPresent();
        }

        if (duplicate) {
            throw new DuplicateRoleAssignmentException("An active role assignment already exists for this role and scope.");
        }
    }

    private boolean isMemberActiveOwner(UUID tenantId, UUID membershipId) {
        return userRoleAssignmentRepository.countEligibleOwners(tenantId, null, null, null) > 0
                && userRoleAssignmentRepository.findAllByTenantIdAndMembershipIdAndIsActiveTrue(tenantId, membershipId)
                .stream()
                .anyMatch(a -> a.getScopeType() == RoleScopeType.TENANT
                        && roleRepository.findById(a.getRoleId()).map(Role::isOwner).orElse(false));
    }
}
