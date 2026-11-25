import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { roleApi } from '../api/roleApi';
import {
  CreateRoleRequest,
  UpdateRoleRequest,
  CreateRoleAssignmentRequest,
} from '../model/types';
import { permissionKeys } from '@/entities/permission/model/permissionQueries';

export const roleKeys = {
  all: ['roles'] as const,
  list: (tenantId: string | undefined) => [...roleKeys.all, 'list', tenantId] as const,
  detail: (tenantId: string | undefined, id: string) =>
    [...roleKeys.all, 'detail', tenantId, id] as const,
  permissions: (tenantId: string | undefined, id: string) =>
    [...roleKeys.all, 'permissions', tenantId, id] as const,
  assignments: (tenantId: string | undefined) => ['role-assignments', tenantId] as const,
  memberRoles: (tenantId: string | undefined, membershipId: string) =>
    ['role-assignments', tenantId, 'member', membershipId] as const,
};

export function useRoles(options?: { enabled?: boolean }) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: roleKeys.list(activeTenant?.tenantId),
    queryFn: () => roleApi.getRoles(),
    enabled: isAuthenticated && !!activeTenant && (options?.enabled ?? true),
    staleTime: 60 * 1000,
  });
}

export function useRole(roleId: string) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: roleKeys.detail(activeTenant?.tenantId, roleId),
    queryFn: () => roleApi.getRoleById(roleId),
    enabled: isAuthenticated && !!activeTenant && !!roleId,
  });
}

export function useRolePermissions(roleId: string) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: roleKeys.permissions(activeTenant?.tenantId, roleId),
    queryFn: () => roleApi.getRolePermissions(roleId),
    enabled: isAuthenticated && !!activeTenant && !!roleId,
  });
}

export function useCreateRole() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (data: CreateRoleRequest) => roleApi.createRole(data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: roleKeys.list(activeTenant?.tenantId),
      });
    },
  });
}

export function useUpdateRole() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ roleId, data }: { roleId: string; data: UpdateRoleRequest }) =>
      roleApi.updateRole(roleId, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: roleKeys.list(activeTenant?.tenantId),
      });
      queryClient.invalidateQueries({
        queryKey: roleKeys.detail(activeTenant?.tenantId, variables.roleId),
      });
    },
  });
}

export function useDeleteRole() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (roleId: string) => roleApi.deleteRole(roleId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: roleKeys.list(activeTenant?.tenantId),
      });
    },
  });
}

export function useUpdateRolePermissions() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ roleId, permissions }: { roleId: string; permissions: string[] }) =>
      roleApi.updateRolePermissions(roleId, permissions),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: roleKeys.list(activeTenant?.tenantId),
      });
      queryClient.invalidateQueries({
        queryKey: roleKeys.detail(activeTenant?.tenantId, variables.roleId),
      });
      queryClient.invalidateQueries({
        queryKey: roleKeys.permissions(activeTenant?.tenantId, variables.roleId),
      });
      // Invalidate current user permissions as permissions might have updated
      queryClient.invalidateQueries({
        queryKey: permissionKeys.mine(activeTenant?.tenantId),
      });
    },
  });
}

export function useRoleAssignments(membershipId?: string, options?: { enabled?: boolean }) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: [...roleKeys.assignments(activeTenant?.tenantId), membershipId],
    queryFn: () => roleApi.getRoleAssignments(membershipId),
    enabled: isAuthenticated && !!activeTenant && (options?.enabled ?? true),
    staleTime: 30 * 1000,
  });
}

export function useAssignRole() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (data: CreateRoleAssignmentRequest) => roleApi.assignRole(data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: roleKeys.assignments(activeTenant?.tenantId),
      });
      queryClient.invalidateQueries({
        queryKey: permissionKeys.mine(activeTenant?.tenantId),
      });
    },
  });
}

export function useRevokeRoleAssignment() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (assignmentId: string) => roleApi.revokeRoleAssignment(assignmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: roleKeys.assignments(activeTenant?.tenantId),
      });
      queryClient.invalidateQueries({
        queryKey: permissionKeys.mine(activeTenant?.tenantId),
      });
    },
  });
}
