import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { useCatalogReadBranchScope } from '@/entities/permission';
import { categoryApi } from '../api/categoryApi';
import {
  CreateCategoryRequest,
  MoveCategoryRequest,
  UpdateCategoryRequest,
} from '../model/types';

export const categoryKeys = {
  all: (tenantId: string | undefined) => ['catalog', tenantId, 'categories'] as const,
  flat: (tenantId: string | undefined, includeInactive: boolean, branchId?: string) =>
    [...categoryKeys.all(tenantId), 'flat', { includeInactive, branchId: branchId ?? null }] as const,
  tree: (tenantId: string | undefined, includeInactive: boolean, branchId?: string) =>
    [...categoryKeys.all(tenantId), 'tree', { includeInactive, branchId: branchId ?? null }] as const,
  detail: (tenantId: string | undefined, categoryId: string, branchId?: string) =>
    [...categoryKeys.all(tenantId), 'detail', categoryId, branchId ?? null] as const,
};

export function useCategories(options?: {
  includeInactive?: boolean;
  branchId?: string;
  enabled?: boolean;
}) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const scope = useCatalogReadBranchScope('catalog.categories.read', options?.branchId);
  const includeInactive = options?.includeInactive ?? false;

  return useQuery({
    queryKey: categoryKeys.flat(activeTenant?.tenantId, includeInactive, scope.resolvedBranchId),
    queryFn: () =>
      categoryApi.listCategories({
        includeInactive,
        branchId: scope.resolvedBranchId,
      }),
    enabled:
      isAuthenticated && !!activeTenant && scope.canRead && (options?.enabled ?? true),
    staleTime: 5 * 60 * 1000,
  });
}

export function useCategoryTree(options?: {
  includeInactive?: boolean;
  branchId?: string;
  enabled?: boolean;
}) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const scope = useCatalogReadBranchScope('catalog.categories.read', options?.branchId);
  const includeInactive = options?.includeInactive ?? false;

  return useQuery({
    queryKey: categoryKeys.tree(activeTenant?.tenantId, includeInactive, scope.resolvedBranchId),
    queryFn: () =>
      categoryApi.getCategoryTree({
        includeInactive,
        branchId: scope.resolvedBranchId,
      }),
    enabled:
      isAuthenticated && !!activeTenant && scope.canRead && (options?.enabled ?? true),
    staleTime: 5 * 60 * 1000,
  });
}

export function useCreateCategory() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (data: CreateCategoryRequest) => categoryApi.createCategory(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useUpdateCategory() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ categoryId, data }: { categoryId: string; data: UpdateCategoryRequest }) =>
      categoryApi.updateCategory(categoryId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all(activeTenant?.tenantId) });
      queryClient.invalidateQueries({ queryKey: ['catalog', activeTenant?.tenantId, 'products'] });
    },
  });
}

export function useMoveCategory() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ categoryId, data }: { categoryId: string; data: MoveCategoryRequest }) =>
      categoryApi.moveCategory(categoryId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useUpdateCategoryStatus() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ categoryId, isActive }: { categoryId: string; isActive: boolean }) =>
      categoryApi.updateCategoryStatus(categoryId, isActive),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useDeleteCategory() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (categoryId: string) => categoryApi.deleteCategory(categoryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all(activeTenant?.tenantId) });
    },
  });
}
