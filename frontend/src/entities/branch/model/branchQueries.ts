import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { branchApi } from '../api/branchApi';
import { CreateBranchRequest } from '../model/types';

export const branchKeys = {
  all: ['branches'] as const,
  list: (tenantId: string | undefined) => [...branchKeys.all, 'list', tenantId] as const,
  detail: (id: string) => [...branchKeys.all, 'detail', id] as const,
};

export function useBranches() {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: branchKeys.list(activeTenant?.tenantId),
    queryFn: () => branchApi.getBranches(),
    enabled: isAuthenticated && !!activeTenant,
    staleTime: 60 * 1000,
  });
}

export function useBranch(id: string) {
  return useQuery({
    queryKey: branchKeys.detail(id),
    queryFn: () => branchApi.getBranchById(id),
    enabled: !!id,
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
