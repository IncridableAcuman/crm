import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { permissionApi } from '../api/permissionApi';

export const permissionKeys = {
  all: ['permissions'] as const,
  mine: (tenantId: string | undefined) => [...permissionKeys.all, 'mine', tenantId] as const,
  catalog: () => [...permissionKeys.all, 'catalog'] as const,
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

export function usePermissionsCatalog() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: permissionKeys.catalog(),
    queryFn: () => permissionApi.getAllPermissions(),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to check if current user has a specific permission in the active store,
 * optionally scoped to a branch.
 */
export function useHasPermission(permissionCode: string, branchId?: string): boolean {
  const { data: permissions } = useMyPermissions();

  if (!permissions) {
    return false;
  }

  // Tenant-level check
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
