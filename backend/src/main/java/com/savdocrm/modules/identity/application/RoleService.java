package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.application.dto.CreateRoleRequest;
import com.savdocrm.modules.identity.application.dto.RoleResponse;
import com.savdocrm.modules.identity.application.dto.UpdateRoleRequest;
import com.savdocrm.modules.identity.domain.Permission;
import com.savdocrm.modules.identity.domain.Permissions;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RolePermission;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.SystemRoles;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.exception.DuplicateRoleCodeException;
import com.savdocrm.modules.identity.domain.exception.InactiveMembershipException;
import com.savdocrm.modules.identity.domain.exception.PermissionDeniedException;
import com.savdocrm.modules.identity.domain.exception.RoleNotFoundException;
import com.savdocrm.modules.identity.domain.exception.SystemRoleImmutableException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service for tenant role lifecycle management, custom role permission updates,
 * immutability guarantees for system roles, and Grant-What-You-Hold containment enforcement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final PermissionAuthorizationService permissionAuthorizationService;

    /**
     * Retrieves all roles (system and custom) defined in the tenant.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> getRoles(UUID tenantId) {
        List<Role> roles = roleRepository.findAllByTenantId(tenantId);
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }

        return roles.stream()
                .map(role -> {
                    Set<String> perms = resolveRolePermissions(tenantId, role);
                    return RoleResponse.fromEntity(role, perms);
                })
                .toList();
    }

    /**
     * Retrieves role details by ID within the tenant.
     */
    @Transactional(readOnly = true)
    public RoleResponse getRole(UUID tenantId, UUID roleId) {
        Role role = roleRepository.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        Set<String> perms = resolveRolePermissions(tenantId, role);
        return RoleResponse.fromEntity(role, perms);
    }

    /**
     * Creates a custom role within the active tenant.
     * Enforces unique role codes, system role code protection, and Grant-What-You-Hold for initial permissions.
     */
    @Transactional
    public RoleResponse createRole(UUID tenantId, CreateRoleRequest request, UUID actorUserId) {
        log.info("Creating custom role '{}' in tenant {} by actor {}", request.code(), tenantId, actorUserId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));
        if (!tenant.isActive()) {
            throw new IllegalStateException("Cannot create roles in an inactive or suspended tenant.");
        }

        String normalizedCode = request.code().trim().toUpperCase();

        if (SystemRoles.isSystemRole(normalizedCode)) {
            throw new DuplicateRoleCodeException("Role code '" + normalizedCode + "' is reserved for built-in system roles.");
        }

        if (roleRepository.existsByTenantIdAndCode(tenantId, normalizedCode)) {
            throw new DuplicateRoleCodeException("A role with code '" + normalizedCode + "' already exists in this tenant.");
        }

        Set<String> initialPermissions = validateAndExtractPermissions(request.permissions());
        if (!initialPermissions.isEmpty()) {
            enforceGrantWhatYouHold(tenantId, actorUserId, initialPermissions, Collections.emptySet());
        }

        Role role = Role.builder()
                .code(normalizedCode)
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .isSystem(false)
                .isActive(true)
                .build();
        role.setTenantId(tenantId);
        role.setCreatedBy(actorUserId);
        role.setUpdatedBy(actorUserId);

        Role savedRole = roleRepository.save(role);

        if (!initialPermissions.isEmpty()) {
            saveRolePermissions(tenantId, savedRole.getId(), initialPermissions);
        }

        return RoleResponse.fromEntity(savedRole, initialPermissions);
    }

    /**
     * Updates a custom role's name and description. System roles reject updates.
     */
    @Transactional
    public RoleResponse updateRole(UUID tenantId, UUID roleId, UpdateRoleRequest request, UUID actorUserId) {
        log.info("Updating role {} in tenant {} by actor {}", roleId, tenantId, actorUserId);

        Role role = roleRepository.findByTenantIdAndIdWithLock(tenantId, roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (role.isSystem()) {
            throw new SystemRoleImmutableException("Built-in system roles cannot be modified.");
        }

        role.update(request.name(), request.description());
        role.setUpdatedBy(actorUserId);
        Role savedRole = roleRepository.save(role);

        Set<String> perms = resolveRolePermissions(tenantId, savedRole);
        return RoleResponse.fromEntity(savedRole, perms);
    }

    /**
     * Deletes or soft-deactivates a custom role.
     * System roles reject deletion. If assignments exist, soft-deactivates the role to preserve audit history.
     */
    @Transactional
    public void deleteOrDeactivateRole(UUID tenantId, UUID roleId, UUID actorUserId) {
        log.info("Deleting or deactivating role {} in tenant {} by actor {}", roleId, tenantId, actorUserId);

        Role role = roleRepository.findByTenantIdAndIdWithLock(tenantId, roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (role.isSystem()) {
            throw new SystemRoleImmutableException("Built-in system roles cannot be deleted or deactivated.");
        }

        boolean hasAssignments = userRoleAssignmentRepository.existsByTenantIdAndRoleId(tenantId, roleId);
        if (hasAssignments) {
            log.info("Role {} has historical assignments. Soft-deactivating to preserve audit lineage.", roleId);
            role.deactivate();
            role.setUpdatedBy(actorUserId);
            roleRepository.save(role);
        } else {
            log.info("Role {} has no assignments. Physically deleting role and permission mappings.", roleId);
            rolePermissionRepository.deleteAllByTenantIdAndRoleId(tenantId, roleId);
            roleRepository.delete(role);
        }
    }

    /**
     * Retrieves all permission codes associated with a role.
     */
    @Transactional(readOnly = true)
    public Set<String> getRolePermissions(UUID tenantId, UUID roleId) {
        Role role = roleRepository.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        return resolveRolePermissions(tenantId, role);
    }

    /**
     * Updates the permission mappings for a custom role.
     * System roles reject updates. Enforces Grant-What-You-Hold on newly added permissions.
     */
    @Transactional
    public Set<String> updateRolePermissions(UUID tenantId, UUID roleId, List<String> requestedPermissions, UUID actorUserId) {
        log.info("Updating permissions for role {} in tenant {} by actor {}", roleId, tenantId, actorUserId);

        Role role = roleRepository.findByTenantIdAndIdWithLock(tenantId, roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (role.isSystem()) {
            throw new SystemRoleImmutableException("Built-in system role permissions cannot be modified.");
        }

        Set<String> newPermCodes = validateAndExtractPermissions(requestedPermissions);
        Set<String> currentPermCodes = rolePermissionRepository.findPermissionCodesByTenantIdAndRoleId(tenantId, roleId);

        // Enforce Grant-What-You-Hold on newly added permissions
        enforceGrantWhatYouHold(tenantId, actorUserId, newPermCodes, currentPermCodes);

        Set<String> toRemove = currentPermCodes.stream()
                .filter(p -> !newPermCodes.contains(p))
                .collect(Collectors.toSet());
        Set<String> toAdd = newPermCodes.stream()
                .filter(p -> !currentPermCodes.contains(p))
                .collect(Collectors.toSet());

        if (!toRemove.isEmpty()) {
            List<RolePermission> existing = rolePermissionRepository.findAllByTenantIdAndRoleId(tenantId, roleId);
            Map<UUID, String> permCodeById = permissionRepository.findAllById(
                    existing.stream().map(RolePermission::getPermissionId).collect(Collectors.toSet())
            ).stream().collect(Collectors.toMap(Permission::getId, Permission::getCode));

            List<RolePermission> toDelete = existing.stream()
                    .filter(rp -> toRemove.contains(permCodeById.get(rp.getPermissionId())))
                    .toList();
            rolePermissionRepository.deleteAll(toDelete);
        }

        if (!toAdd.isEmpty()) {
            saveRolePermissions(tenantId, roleId, toAdd);
        }

        role.setUpdatedBy(actorUserId);
        roleRepository.save(role);

        return Collections.unmodifiableSet(newPermCodes);
    }

    @Transactional
    public Set<String> updateRolePermissions(UUID tenantId, UUID roleId, Set<String> requestedPermissions, UUID actorUserId) {
        return updateRolePermissions(
                tenantId,
                roleId,
                requestedPermissions != null ? new ArrayList<>(requestedPermissions) : Collections.emptyList(),
                actorUserId
        );
    }

    private void enforceGrantWhatYouHold(UUID tenantId, UUID actorUserId, Set<String> requestedPermissions, Set<String> existingPermissions) {
        if (actorUserId == null) {
            throw new PermissionDeniedException("Unauthenticated actors cannot modify role permissions.");
        }

        TenantMembership actorMembership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, actorUserId)
                .orElseThrow(() -> new PermissionDeniedException("Actor is not a member of tenant: " + tenantId));

        if (!actorMembership.isActive()) {
            throw new InactiveMembershipException("Actor membership is not ACTIVE.");
        }

        boolean isOwner = isMemberActiveOwner(tenantId, actorMembership.getId());
        if (isOwner) {
            return;
        }

        if (!permissionAuthorizationService.hasPermission(tenantId, actorMembership.getId(), Permissions.IDENTITY_ROLES_MANAGE)) {
            throw new PermissionDeniedException("Actor lacks permission 'identity.roles.manage' to modify role permissions.");
        }

        Set<String> newlyAddedPermissions = requestedPermissions.stream()
                .filter(p -> !existingPermissions.contains(p))
                .collect(Collectors.toSet());

        if (newlyAddedPermissions.isEmpty()) {
            return;
        }

        Set<String> actorEffectivePermissions = permissionAuthorizationService.getEffectiveTenantPermissions(tenantId, actorMembership.getId());

        if (!actorEffectivePermissions.containsAll(newlyAddedPermissions)) {
            Set<String> missing = newlyAddedPermissions.stream()
                    .filter(p -> !actorEffectivePermissions.contains(p))
                    .collect(Collectors.toSet());
            throw new PermissionDeniedException("Containment violation: Cannot grant permissions you do not actively possess: " + missing);
        }
    }

    private boolean isMemberActiveOwner(UUID tenantId, UUID membershipId) {
        return userRoleAssignmentRepository.countEligibleOwners(tenantId, null, null, null) > 0
                && userRoleAssignmentRepository.findAllByTenantIdAndMembershipIdAndIsActiveTrue(tenantId, membershipId)
                .stream()
                .anyMatch(a -> a.getScopeType() == RoleScopeType.TENANT
                        && roleRepository.findById(a.getRoleId()).map(Role::isOwner).orElse(false));
    }

    private Set<String> validateAndExtractPermissions(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> seen = new HashSet<>();
        for (String code : permissionCodes) {
            if (code == null || code.trim().isBlank()) {
                throw new IllegalArgumentException("Permission code cannot be blank.");
            }
            String trimmed = code.trim();
            if (!Permissions.isValidCode(trimmed)) {
                throw new IllegalArgumentException("Unknown permission code: " + trimmed);
            }
            if (!seen.add(trimmed)) {
                throw new IllegalArgumentException("Duplicate permission code: " + trimmed);
            }
        }
        return seen;
    }

    private void saveRolePermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes) {
        List<Permission> permissions = permissionRepository.findAllByCodeIn(permissionCodes);
        List<RolePermission> mappings = permissions.stream()
                .map(p -> RolePermission.builder()
                        .tenantId(tenantId)
                        .roleId(roleId)
                        .permissionId(p.getId())
                        .build())
                .toList();
        rolePermissionRepository.saveAll(mappings);
    }

    private Set<String> resolveRolePermissions(UUID tenantId, Role role) {
        if (role.isOwner()) {
            return Permissions.ALL_PERMISSIONS;
        }
        return rolePermissionRepository.findPermissionCodesByTenantIdAndRoleId(tenantId, role.getId());
    }
}
