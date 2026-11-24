package com.savdocrm.modules.identity.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core RBAC Permission Authorization Service.
 * Evaluates fine-grained permissions against active tenant memberships and assignments.
 * Enforces TENANT and BRANCH scopes, live entity statuses, and fail-closed security.
 */
public interface PermissionAuthorizationService {

    /**
     * Evaluates whether the currently authenticated principal possesses the specified permission at TENANT scope.
     *
     * @param permissionCode Canonical dot notation permission code (e.g. "catalog.products.create")
     * @return true if authorized at store-wide TENANT scope, false otherwise
     */
    boolean hasPermission(String permissionCode);

    /**
     * Evaluates whether the currently authenticated principal possesses the specified permission for the given branch.
     * Satisfied if the principal holds the permission at store-wide TENANT scope OR explicitly at BRANCH scope for branchId.
     *
     * @param permissionCode Canonical dot notation permission code
     * @param branchId       Target store branch identifier
     * @return true if authorized for the target branch, false otherwise
     */
    boolean hasBranchPermission(String permissionCode, UUID branchId);

    /**
     * Explicit tenant-scoped authorization evaluation for a specific membership.
     *
     * @param tenantId       Target tenant identifier
     * @param membershipId   Target tenant membership identifier
     * @param permissionCode Canonical dot notation permission code
     * @return true if authorized at store-wide TENANT scope, false otherwise
     */
    boolean hasPermission(UUID tenantId, UUID membershipId, String permissionCode);

    /**
     * Explicit branch-scoped authorization evaluation for a specific membership.
     *
     * @param tenantId       Target tenant identifier
     * @param membershipId   Target tenant membership identifier
     * @param permissionCode Canonical dot notation permission code
     * @param branchId       Target store branch identifier
     * @return true if authorized for the target branch, false otherwise
     */
    boolean hasBranchPermission(UUID tenantId, UUID membershipId, String permissionCode, UUID branchId);

    /**
     * Retrieves all active permission codes granted at store-wide TENANT scope for the membership.
     */
    Set<String> getEffectiveTenantPermissions(UUID tenantId, UUID membershipId);

    /**
     * Retrieves effective permissions grouped by branch for the membership.
     * Includes store-wide permissions inherited across branches.
     */
    Map<UUID, Set<String>> getEffectiveBranchPermissions(UUID tenantId, UUID membershipId);
}
