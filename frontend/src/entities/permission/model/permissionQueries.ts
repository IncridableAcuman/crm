import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { permissionApi } from '../api/permissionApi';

export const permissionKeys = {
  all: ['permissions'] as const,
  mine: (tenantId: string | undefined) => [...permissionKeys.all, 'mine', tenantId] as const,
  catalog: (tenantId: string | undefined) => [...permissionKeys.all, 'catalog', tenantId] as const,
};

export function useMyPermissions() {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: permissionKeys.mine(activeTenant?.tenantId),
    queryFn: () => permissionApi.getMyPermissions(),
    enabled: isAuthenticated && !!activeTenant,
    staleTime: 60 * 1000, // 1 minute
  });
}

export function usePermissionsCatalog(options?: { enabled?: boolean }) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const canReadRoles = useHasPermission('identity.roles.read');

  return useQuery({
    queryKey: permissionKeys.catalog(activeTenant?.tenantId),
    queryFn: () => permissionApi.getAllPermissions(),
    enabled: isAuthenticated && !!activeTenant && canReadRoles && (options?.enabled ?? true),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to check if current user has a specific permission in the active store,
 * optionally scoped to a branch.
 * Fail-closed: returns false while loading, on error, or when unauthenticated.
 */
export function useHasPermission(permissionCode: string, branchId?: string): boolean {
  const { data: permissions } = useMyPermissions();

  if (!permissions) {
    return false;
  }

  // Tenant-level check (TENANT scope satisfies both tenant and branch checks)
  if (permissions.tenantPermissions?.includes(permissionCode)) {
    return true;
  }

  // Branch-level check if branchId provided
  if (branchId && permissions.branchPermissions) {
    const branchPerms = permissions.branchPermissions[branchId];
    if (branchPerms && branchPerms.includes(permissionCode)) {
      return true;
    }
  }

  return false;
}

/**
 * Checks whether the current user holds a permission at TENANT scope OR at least one BRANCH scope.
 * Useful for visibility of branch-list pages where branch-scoped roles (e.g. MANAGER, CASHIER)
 * hold `tenancy.branches.read` on their assigned branch.
 * Fail-closed: returns false while loading, on error, or when unauthenticated.
 */
export function useHasAnyScopePermission(permissionCode: string): boolean {
  const { data: permissions } = useMyPermissions();

  if (!permissions) {
    return false;
  }

  if (permissions.tenantPermissions?.includes(permissionCode)) {
    return true;
  }

  if (permissions.branchPermissions) {
    return Object.values(permissions.branchPermissions).some((perms) =>
      perms.includes(permissionCode)
    );
  }

  return false;
}

export interface CatalogReadBranchScope {
  canRead: boolean;
  isTenantScoped: boolean;
  authorizedBranchIds: string[];
  resolvedBranchId: string | undefined;
  invalidBranchRequested: boolean;
}

/**
 * Resolves the required branchId parameter for M05.4 catalog read endpoints.
 * - TENANT-scoped holders can read without branchId (resolvedBranchId = undefined unless explicitly requested).
 * - BRANCH-only holders MUST pass an active authorized branchId on catalog GET requests;
 *   without it, the backend fails closed with 403 Forbidden (ADR-0010).
 * - Explicit unauthorized preferredBranchId fails closed (canRead = false, invalidBranchRequested = true)
 *   rather than silently falling back to another branch.
 */
export function useCatalogReadBranchScope(
  permissionCode: string,
  preferredBranchId?: string
): CatalogReadBranchScope {
  const { data: permissions } = useMyPermissions();

  if (!permissions) {
    return {
      canRead: false,
      isTenantScoped: false,
      authorizedBranchIds: [],
      resolvedBranchId: undefined,
      invalidBranchRequested: false,
    };
  }

  const isTenantScoped = Boolean(permissions.tenantPermissions?.includes(permissionCode));
  const authorizedBranchIds = permissions.branchPermissions
    ? Object.entries(permissions.branchPermissions)
        .filter(([, perms]) => perms.includes(permissionCode))
        .map(([branchId]) => branchId)
    : [];

  if (isTenantScoped) {
    return {
      canRead: true,
      isTenantScoped: true,
      authorizedBranchIds,
      resolvedBranchId: preferredBranchId || undefined,
      invalidBranchRequested: false,
    };
  }

  if (preferredBranchId && !authorizedBranchIds.includes(preferredBranchId)) {
    return {
      canRead: false,
      isTenantScoped: false,
      authorizedBranchIds,
      resolvedBranchId: undefined,
      invalidBranchRequested: true,
    };
  }

  const firstBranchId = authorizedBranchIds[0];
  const resolvedBranchId = preferredBranchId || firstBranchId;

  return {
    canRead: authorizedBranchIds.length > 0 && Boolean(resolvedBranchId),
    isTenantScoped: false,
    authorizedBranchIds,
    resolvedBranchId,
    invalidBranchRequested: false,
  };
}
