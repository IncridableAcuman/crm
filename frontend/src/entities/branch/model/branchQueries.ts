import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { branchApi } from '../api/branchApi';
import { CreateBranchRequest } from '../model/types';

export const branchKeys = {
  all: ['branches'] as const,
  list: (tenantId: string | undefined) => [...branchKeys.all, 'list', tenantId] as const,
  detail: (tenantId: string | undefined, id: string) =>
    [...branchKeys.all, 'detail', tenantId, id] as const,
};

export function useBranches(options?: { enabled?: boolean }) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: branchKeys.list(activeTenant?.tenantId),
    queryFn: () => branchApi.getBranches(),
    enabled: isAuthenticated && !!activeTenant && (options?.enabled ?? true),
    staleTime: 60 * 1000,
  });
}

export function useBranch(id: string, options?: { enabled?: boolean }) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: branchKeys.detail(activeTenant?.tenantId, id),
    queryFn: () => branchApi.getBranchById(id),
    enabled: isAuthenticated && !!activeTenant && !!id && (options?.enabled ?? true),
    staleTime: 60 * 1000,
  });
}

export function useCreateBranch() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (data: CreateBranchRequest) => branchApi.createBranch(data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: branchKeys.list(activeTenant?.tenantId),
      });
    },
  });
}
