package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.domain.Permission;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RolePermission;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.domain.exception.CrossTenantAssignmentException;
import com.savdocrm.modules.identity.domain.exception.InactiveMembershipException;
import com.savdocrm.modules.identity.domain.exception.RoleNotFoundException;
import com.savdocrm.modules.identity.domain.exception.TenantBootstrapNotAllowedException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.PermissionRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RolePermissionRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application service for secure tenant initial OWNER bootstrap and system role templates seeding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBootstrapService {

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    /**
     * Atomically seeds system roles, permission mappings, and assigns the initial OWNER role
     * to the creator's membership during new tenant creation.
     */
    @Transactional
    public void initializeNewTenantRbac(UUID tenantId, UUID creatorMembershipId) {
        log.info("Initializing RBAC system roles and assigning initial OWNER for new tenant: {}", tenantId);

        TenantMembership membership = tenantMembershipRepository.findById(creatorMembershipId)
                .orElseThrow(() -> new InactiveMembershipException("Creator membership not found: " + creatorMembershipId));

        if (!membership.getTenantId().equals(tenantId)) {
            throw new CrossTenantAssignmentException("Creator membership does not belong to tenant: " + tenantId);
        }

        if (!membership.isActive()) {
            throw new InactiveMembershipException("Creator membership must be ACTIVE.");
        }

        // 1. Seed the 7 system roles and their permission mappings
        Map<String, Role> seededRoles = seedSystemRolesAndPermissions(tenantId);

        // 2. Assign OWNER role to creator membership at TENANT scope
        Role ownerRole = seededRoles.get(SystemRoles.OWNER);
        if (ownerRole == null) {
            throw new RoleNotFoundException("OWNER role failed to seed for tenant: " + tenantId);
        }

        UserRoleAssignment ownerAssignment = UserRoleAssignment.builder()
                .membershipId(creatorMembershipId)
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        ownerAssignment.setTenantId(tenantId);

        userRoleAssignmentRepository.save(ownerAssignment);
        log.info("Successfully established initial OWNER for new tenant: {}", tenantId);
    }

    /**
     * Bootstraps an initial OWNER for an existing unbootstrapped tenant (created prior to M04).
     * Serialized via pessimistic write lock on the parent Tenant row.
     */
    @Transactional
    public TenantBootstrapResult bootstrapExistingTenantOwner(UUID tenantId, UUID targetMembershipId, UUID actorUserId) {
        log.info("Attempting bootstrap for existing tenant: {} by actor: {}", tenantId, actorUserId);

        // 1. Concurrency-safe pessimistic write lock on parent tenant row
        Tenant tenant = tenantRepository.findByIdWithLock(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));

        if (!tenant.isActive()) {
            throw new IllegalStateException("Cannot bootstrap an inactive or suspended tenant: " + tenantId);
        }

        // 2. Re-evaluate eligible OWNER count under exclusive row lock
        long existingOwnerCount = userRoleAssignmentRepository.countEligibleOwners(tenantId, null, null, null);
        if (existingOwnerCount > 0) {
            log.info("Tenant {} is already bootstrapped ({} eligible active owners present). No-op.", tenantId, existingOwnerCount);
            return TenantBootstrapResult.ALREADY_BOOTSTRAPPED;
        }

        // 3. Security Check: Ordinary ADMIN users cannot bootstrap themselves to OWNER
        if (actorUserId != null) {
            TenantMembership actorMembership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, actorUserId).orElse(null);
            if (actorMembership != null) {
                // If actor is just a regular member of the tenant (and not the original tenant creator),
                // check if they are trying to self-elevate
                boolean isCreator = Objects.equals(tenant.getCreatedBy(), actorUserId);
                if (!isCreator) {
                    throw new TenantBootstrapNotAllowedException("Ordinary tenant users/administrators cannot bootstrap or self-elevate to OWNER.");
                }
            }
        }

        // 4. Validate target membership
        TenantMembership targetMembership = tenantMembershipRepository.findById(targetMembershipId)
                .orElseThrow(() -> new InactiveMembershipException("Target membership not found: " + targetMembershipId));

        if (!targetMembership.getTenantId().equals(tenantId)) {
            throw new CrossTenantAssignmentException("Target membership does not belong to tenant: " + tenantId);
        }

        if (!targetMembership.isActive()) {
            throw new InactiveMembershipException("Target membership must be ACTIVE.");
        }

        User user = userRepository.findById(targetMembership.getUserId())
                .orElseThrow(() -> new IllegalStateException("Underlying user not found: " + targetMembership.getUserId()));

        if (!user.isActive()) {
            throw new IllegalStateException("Underlying user is not ACTIVE.");
        }

        // 5. Ensure system roles exist for this tenant
        Map<String, Role> roles = seedSystemRolesAndPermissions(tenantId);
        Role ownerRole = roles.get(SystemRoles.OWNER);
        if (ownerRole == null) {
            throw new RoleNotFoundException("OWNER role not found for tenant: " + tenantId);
        }

        // 6. Create active OWNER assignment
        UserRoleAssignment ownerAssignment = UserRoleAssignment.builder()
                .membershipId(targetMembershipId)
                .roleId(ownerRole.getId())
                .scopeType(RoleScopeType.TENANT)
                .branchId(null)
                .isActive(true)
                .build();
        ownerAssignment.setTenantId(tenantId);

        userRoleAssignmentRepository.save(ownerAssignment);
        log.info("Successfully bootstrapped initial OWNER for existing tenant: {}", tenantId);
        return TenantBootstrapResult.SUCCESS;
    }

    /**
     * Lists active tenants lacking an eligible active OWNER.
     */
    @Transactional(readOnly = true)
    public List<UUID> findUnbootstrappedTenants() {
        return userRoleAssignmentRepository.findUnbootstrappedActiveTenantIds();
    }

    /**
     * Seeds all 7 system role templates and permissions for a tenant if not already present.
     */
    @Transactional
    public Map<String, Role> seedSystemRolesAndPermissions(UUID tenantId) {
        Map<String, Permission> permissionsByCode = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getCode, Function.identity()));

        List<Role> existingRoles = roleRepository.findAllByTenantId(tenantId);
        Map<String, Role> roleMap = existingRoles.stream()
                .collect(Collectors.toMap(Role::getCode, Function.identity()));

        for (SystemRoles.SystemRoleTemplate template : SystemRoles.TEMPLATES) {
            Role role = roleMap.get(template.code());
            if (role == null) {
                role = Role.builder()
                        .code(template.code())
                        .name(template.name())
                        .description(template.description())
                        .isSystem(true)
                        .isActive(true)
                        .build();
                role.setTenantId(tenantId);
                role = roleRepository.save(role);
                roleMap.put(template.code(), role);
            }

            // Seed role permissions if missing
            Set<String> existingPermCodes = rolePermissionRepository.findPermissionCodesByTenantIdAndRoleId(tenantId, role.getId());
            List<RolePermission> newRolePermissions = new ArrayList<>();

            for (String permCode : template.permissions()) {
                if (!existingPermCodes.contains(permCode)) {
                    Permission perm = permissionsByCode.get(permCode);
                    if (perm != null) {
                        RolePermission rp = RolePermission.builder()
                                .tenantId(tenantId)
                                .roleId(role.getId())
                                .permissionId(perm.getId())
                                .build();
                        newRolePermissions.add(rp);
                    }
                }
            }

            if (!newRolePermissions.isEmpty()) {
                rolePermissionRepository.saveAll(newRolePermissions);
            }
        }

        return roleMap;
    }
}
