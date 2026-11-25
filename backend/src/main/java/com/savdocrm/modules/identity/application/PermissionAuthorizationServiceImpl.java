package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.domain.Permissions;
import com.savdocrm.modules.identity.domain.Role;
import com.savdocrm.modules.identity.domain.RoleScopeType;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserRoleAssignment;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RolePermissionRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.RoleRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.identity.infrastructure.security.RequestScopedPermissionMemoizer;
import com.savdocrm.modules.tenancy.domain.Branch;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.infrastructure.BranchRepository;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContext;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import com.savdocrm.shared.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service("authorizationService")
@RequiredArgsConstructor
public class PermissionAuthorizationServiceImpl implements PermissionAuthorizationService {

    private final TenantRepository tenantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RequestScopedPermissionMemoizer memoizer;

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }

        ResolvedContext context = resolveCurrentContext();
        if (context == null) {
            return false;
        }

        return hasPermission(context.tenantId(), context.membershipId(), permissionCode);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBranchPermission(String permissionCode, UUID branchId) {
        if (permissionCode == null || permissionCode.isBlank() || branchId == null) {
            return false;
        }

        ResolvedContext context = resolveCurrentContext();
        if (context == null) {
            return false;
        }

        return hasBranchPermission(context.tenantId(), context.membershipId(), permissionCode, branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyScopePermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }

        ResolvedContext context = resolveCurrentContext();
        if (context == null) {
            return false;
        }

        return hasAnyScopePermission(context.tenantId(), context.membershipId(), permissionCode);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID tenantId, UUID membershipId, String permissionCode) {
        if (tenantId == null || membershipId == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }

        if (isRequestScopeActive()) {
            Boolean cached = memoizer.getCachedCheck(tenantId, membershipId, permissionCode, "TENANT", null);
            if (cached != null) {
                return cached;
            }
        }

        Set<String> effectiveTenantPermissions = getEffectiveTenantPermissions(tenantId, membershipId);
        boolean granted = effectiveTenantPermissions.contains(permissionCode);

        if (isRequestScopeActive()) {
            memoizer.putCachedCheck(tenantId, membershipId, permissionCode, "TENANT", null, granted);
        }

        return granted;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBranchPermission(UUID tenantId, UUID membershipId, String permissionCode, UUID branchId) {
        if (tenantId == null || membershipId == null || permissionCode == null || permissionCode.isBlank() || branchId == null) {
            return false;
        }

        if (isRequestScopeActive()) {
            Boolean cached = memoizer.getCachedCheck(tenantId, membershipId, permissionCode, "BRANCH", branchId);
            if (cached != null) {
                return cached;
            }
        }

        // 1. Verify target branch belongs to tenant and is active
        Branch branch = branchRepository.findByTenantIdAndId(tenantId, branchId).orElse(null);
        if (branch == null || !branch.isActive()) {
            if (isRequestScopeActive()) {
                memoizer.putCachedCheck(tenantId, membershipId, permissionCode, "BRANCH", branchId, false);
            }
            return false;
        }

        // 2. Check store-wide TENANT scope permissions first (scope inheritance)
        Set<String> tenantPermissions = getEffectiveTenantPermissions(tenantId, membershipId);
        if (tenantPermissions.contains(permissionCode)) {
            if (isRequestScopeActive()) {
                memoizer.putCachedCheck(tenantId, membershipId, permissionCode, "BRANCH", branchId, true);
            }
            return true;
        }

        // 3. Check branch-specific permissions
        Map<UUID, Set<String>> branchPermissions = getEffectiveBranchPermissions(tenantId, membershipId);
        Set<String> specificBranchPermissions = branchPermissions.getOrDefault(branchId, Collections.emptySet());
        boolean granted = specificBranchPermissions.contains(permissionCode);

        if (isRequestScopeActive()) {
            memoizer.putCachedCheck(tenantId, membershipId, permissionCode, "BRANCH", branchId, granted);
        }

        return granted;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAnyScopePermission(UUID tenantId, UUID membershipId, String permissionCode) {
        if (tenantId == null || membershipId == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }

        if (isRequestScopeActive()) {
            Boolean cached = memoizer.getCachedCheck(tenantId, membershipId, permissionCode, "ANY_SCOPE", null);
            if (cached != null) {
                return cached;
            }
        }

        Set<String> tenantPermissions = getEffectiveTenantPermissions(tenantId, membershipId);
        if (tenantPermissions.contains(permissionCode)) {
            if (isRequestScopeActive()) {
                memoizer.putCachedCheck(tenantId, membershipId, permissionCode, "ANY_SCOPE", null, true);
            }
            return true;
        }

        Map<UUID, Set<String>> branchPermissions = getEffectiveBranchPermissions(tenantId, membershipId);
        boolean granted = false;
        for (Map.Entry<UUID, Set<String>> entry : branchPermissions.entrySet()) {
            if (entry.getValue().contains(permissionCode)) {
                Branch branch = branchRepository.findByTenantIdAndId(tenantId, entry.getKey()).orElse(null);
                if (branch != null && branch.isActive()) {
                    granted = true;
                    break;
                }
            }
        }

        if (isRequestScopeActive()) {
            memoizer.putCachedCheck(tenantId, membershipId, permissionCode, "ANY_SCOPE", null, granted);
        }

        return granted;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getEffectiveTenantPermissions(UUID tenantId, UUID membershipId) {
        if (tenantId == null || membershipId == null) {
            return Collections.emptySet();
        }

        if (isRequestScopeActive()) {
            Set<String> cached = memoizer.getCachedTenantPermissions(tenantId, membershipId);
            if (cached != null) {
                return cached;
            }
        }

        if (!isEntityHierarchyActive(tenantId, membershipId)) {
            return cacheAndReturnTenantPermissions(tenantId, membershipId, Collections.emptySet());
        }

        List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                .findAllByTenantIdAndMembershipIdAndIsActiveTrue(tenantId, membershipId);

        List<UserRoleAssignment> tenantAssignments = assignments.stream()
                .filter(a -> a.getScopeType() == RoleScopeType.TENANT)
                .toList();

        if (tenantAssignments.isEmpty()) {
            return cacheAndReturnTenantPermissions(tenantId, membershipId, Collections.emptySet());
        }

        Set<String> permissions = resolvePermissionsForAssignments(tenantId, tenantAssignments);
        return cacheAndReturnTenantPermissions(tenantId, membershipId, permissions);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Set<String>> getEffectiveBranchPermissions(UUID tenantId, UUID membershipId) {
        if (tenantId == null || membershipId == null) {
            return Collections.emptyMap();
        }

        if (isRequestScopeActive()) {
            Map<UUID, Set<String>> cached = memoizer.getCachedBranchPermissions(tenantId, membershipId);
            if (cached != null) {
                return cached;
            }
        }

        if (!isEntityHierarchyActive(tenantId, membershipId)) {
            return cacheAndReturnBranchPermissions(tenantId, membershipId, Collections.emptyMap());
        }

        List<UserRoleAssignment> assignments = userRoleAssignmentRepository
                .findAllByTenantIdAndMembershipIdAndIsActiveTrue(tenantId, membershipId);

        List<UserRoleAssignment> branchAssignments = assignments.stream()
                .filter(a -> a.getScopeType() == RoleScopeType.BRANCH && a.getBranchId() != null)
                .toList();

        Map<UUID, Set<String>> branchMap = new HashMap<>();

        // Group assignments by branchId
        Map<UUID, List<UserRoleAssignment>> byBranch = branchAssignments.stream()
                .collect(Collectors.groupingBy(UserRoleAssignment::getBranchId));

        for (Map.Entry<UUID, List<UserRoleAssignment>> entry : byBranch.entrySet()) {
            UUID branchId = entry.getKey();
            Set<String> perms = resolvePermissionsForAssignments(tenantId, entry.getValue());
            branchMap.put(branchId, perms);
        }

        return cacheAndReturnBranchPermissions(tenantId, membershipId, branchMap);
    }

    private Set<String> resolvePermissionsForAssignments(UUID tenantId, List<UserRoleAssignment> assignments) {
        if (assignments.isEmpty()) {
            return Collections.emptySet();
        }

        Set<UUID> roleIds = assignments.stream()
                .map(UserRoleAssignment::getRoleId)
                .collect(Collectors.toSet());

        List<Role> activeRoles = roleRepository.findAllById(roleIds).stream()
                .filter(r -> r.getTenantId().equals(tenantId) && r.isActive())
                .toList();

        if (activeRoles.isEmpty()) {
            return Collections.emptySet();
        }

        // If any assigned active role is OWNER, grant full catalog
        boolean hasActiveOwner = activeRoles.stream().anyMatch(Role::isOwner);
        if (hasActiveOwner) {
            return new HashSet<>(Permissions.ALL_PERMISSIONS);
        }

        Set<UUID> nonOwnerRoleIds = activeRoles.stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        List<Object[]> pairs = rolePermissionRepository
                .findRolePermissionPairsByTenantIdAndRoleIds(tenantId, nonOwnerRoleIds);

        Set<String> permissions = new HashSet<>();
        for (Object[] pair : pairs) {
            if (pair.length > 1 && pair[1] instanceof String permCode) {
                permissions.add(permCode);
            }
        }

        return permissions;
    }

    private boolean isEntityHierarchyActive(UUID tenantId, UUID membershipId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || !tenant.isActive()) {
            return false;
        }

        TenantMembership membership = tenantMembershipRepository.findById(membershipId).orElse(null);
        if (membership == null || !membership.getTenantId().equals(tenantId) || !membership.isActive()) {
            return false;
        }

        User user = userRepository.findById(membership.getUserId()).orElse(null);
        return user != null && user.isActive();
    }

    private Set<String> cacheAndReturnTenantPermissions(UUID tenantId, UUID membershipId, Set<String> perms) {
        Set<String> unmodifiable = Collections.unmodifiableSet(perms);
        if (isRequestScopeActive()) {
            memoizer.putCachedTenantPermissions(tenantId, membershipId, unmodifiable);
        }
        return unmodifiable;
    }

    private Map<UUID, Set<String>> cacheAndReturnBranchPermissions(UUID tenantId, UUID membershipId, Map<UUID, Set<String>> map) {
        Map<UUID, Set<String>> unmodifiable = Collections.unmodifiableMap(map);
        if (isRequestScopeActive()) {
            memoizer.putCachedBranchPermissions(tenantId, membershipId, unmodifiable);
        }
        return unmodifiable;
    }

    private boolean isRequestScopeActive() {
        return RequestContextHolder.getRequestAttributes() != null;
    }

    private ResolvedContext resolveCurrentContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return null;
        }

        if (!principal.isActive()) {
            return null;
        }

        TenantContext tenantContext = TenantContextHolder.getContext().orElse(null);
        if (tenantContext == null || tenantContext.tenantId() == null) {
            return null;
        }

        UUID tenantId = tenantContext.tenantId();
        UUID membershipId = principal.getActiveMembershipId();

        if (membershipId == null) {
            return null;
        }

        if (principal.getActiveTenantId() != null && !principal.getActiveTenantId().equals(tenantId)) {
            return null;
        }

        return new ResolvedContext(tenantId, membershipId, principal.getId());
    }

    private record ResolvedContext(UUID tenantId, UUID membershipId, UUID userId) {}
}
