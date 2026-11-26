import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { useCatalogReadBranchScope } from '@/entities/permission';
import { unitApi } from '../api/unitApi';
import { CreateUnitRequest, UpdateUnitRequest } from '../model/types';

export const unitKeys = {
  all: (tenantId: string | undefined) => ['catalog', tenantId, 'units'] as const,
  list: (tenantId: string | undefined, includeInactive: boolean, branchId?: string) =>
    [...unitKeys.all(tenantId), 'list', { includeInactive, branchId: branchId ?? null }] as const,
  detail: (tenantId: string | undefined, unitId: string, branchId?: string) =>
    [...unitKeys.all(tenantId), 'detail', unitId, branchId ?? null] as const,
};

export function useUnits(options?: {
  includeInactive?: boolean;
  branchId?: string;
  enabled?: boolean;
}) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const scope = useCatalogReadBranchScope('catalog.products.read', options?.branchId);
  const includeInactive = options?.includeInactive ?? false;

  return useQuery({
    queryKey: unitKeys.list(activeTenant?.tenantId, includeInactive, scope.resolvedBranchId),
    queryFn: () =>
      unitApi.listUnits({
        includeInactive,
        branchId: scope.resolvedBranchId,
      }),
    enabled:
      isAuthenticated && !!activeTenant && scope.canRead && (options?.enabled ?? true),
    staleTime: 5 * 60 * 1000,
  });
}

export function useUnit(unitId: string, options?: { branchId?: string; enabled?: boolean }) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const scope = useCatalogReadBranchScope('catalog.products.read', options?.branchId);

  return useQuery({
    queryKey: unitKeys.detail(activeTenant?.tenantId, unitId, scope.resolvedBranchId),
    queryFn: () => unitApi.getUnit(unitId, scope.resolvedBranchId),
    enabled:
      isAuthenticated &&
      !!activeTenant &&
      !!unitId &&
      scope.canRead &&
      (options?.enabled ?? true),
    staleTime: 5 * 60 * 1000,
  });
}

export function useCreateUnit() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (data: CreateUnitRequest) => unitApi.createUnit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: unitKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useUpdateUnit() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ unitId, data }: { unitId: string; data: UpdateUnitRequest }) =>
      unitApi.updateUnit(unitId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: unitKeys.all(activeTenant?.tenantId) });
      queryClient.invalidateQueries({ queryKey: ['catalog', activeTenant?.tenantId, 'products'] });
    },
  });
}

export function useUpdateUnitStatus() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ unitId, isActive }: { unitId: string; isActive: boolean }) =>
      unitApi.updateUnitStatus(unitId, isActive),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: unitKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useDeleteUnit() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (unitId: string) => unitApi.deleteUnit(unitId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: unitKeys.all(activeTenant?.tenantId) });
    },
  });
}
