package com.savdocrm.modules.identity.infrastructure.security;

import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Request-scoped permission memoizer.
 * Memoizes resolved tenant permissions and branch permissions strictly for the lifespan of a single HTTP request.
 * Automatically destroyed and garbage-collected when the HTTP request terminates.
 *
 * NOTE: Request scope does not automatically propagate across thread boundaries (e.g. @Async or background jobs).
 * When evaluated outside an active HTTP request context, caller should safely bypass this memoizer and query PostgreSQL directly.
 */
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestScopedPermissionMemoizer {

    private final Map<String, Boolean> evaluationCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> tenantPermissionsCache = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Set<String>>> branchPermissionsCache = new ConcurrentHashMap<>();

    private String buildUserKey(UUID tenantId, UUID membershipId) {
        return tenantId + ":" + membershipId;
    }

    private String buildCheckKey(UUID tenantId, UUID membershipId, String permissionCode, String scopeType, UUID branchId) {
        return tenantId + ":" + membershipId + ":" + permissionCode + ":" + scopeType + ":" + (branchId != null ? branchId : "NONE");
    }

    public Boolean getCachedCheck(UUID tenantId, UUID membershipId, String permissionCode, String scopeType, UUID branchId) {
        return evaluationCache.get(buildCheckKey(tenantId, membershipId, permissionCode, scopeType, branchId));
    }

    public void putCachedCheck(UUID tenantId, UUID membershipId, String permissionCode, String scopeType, UUID branchId, boolean granted) {
        evaluationCache.put(buildCheckKey(tenantId, membershipId, permissionCode, scopeType, branchId), granted);
    }

    public Set<String> getCachedTenantPermissions(UUID tenantId, UUID membershipId) {
        return tenantPermissionsCache.get(buildUserKey(tenantId, membershipId));
    }

    public void putCachedTenantPermissions(UUID tenantId, UUID membershipId, Set<String> permissions) {
        tenantPermissionsCache.put(buildUserKey(tenantId, membershipId), permissions);
    }

    public Map<UUID, Set<String>> getCachedBranchPermissions(UUID tenantId, UUID membershipId) {
        return branchPermissionsCache.get(buildUserKey(tenantId, membershipId));
    }

    public void putCachedBranchPermissions(UUID tenantId, UUID membershipId, Map<UUID, Set<String>> branchPermissions) {
        branchPermissionsCache.put(buildUserKey(tenantId, membershipId), branchPermissions);
    }

    public void clear() {
        evaluationCache.clear();
        tenantPermissionsCache.clear();
        branchPermissionsCache.clear();
    }
}
