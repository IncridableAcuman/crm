import { apiClient } from '@/shared/api/client';
import { ApiResponse } from '@/entities/session/model/types';
import {
  Role,
  CreateRoleRequest,
  UpdateRoleRequest,
  RolePermissionResponse,
  RolePermissionUpdateRequest,
  UserRoleAssignment,
  CreateRoleAssignmentRequest,
} from '../model/types';

export const roleApi = {
  getRoles: async (): Promise<Role[]> => {
    const response = await apiClient.get<ApiResponse<Role[]>>('/roles');
    return response.data.data;
  },

  getRoleById: async (roleId: string): Promise<Role> => {
    const response = await apiClient.get<ApiResponse<Role>>(`/roles/${roleId}`);
    return response.data.data;
  },

  createRole: async (data: CreateRoleRequest): Promise<Role> => {
    const response = await apiClient.post<ApiResponse<Role>>('/roles', data);
    return response.data.data;
  },

  updateRole: async (roleId: string, data: UpdateRoleRequest): Promise<Role> => {
    const response = await apiClient.put<ApiResponse<Role>>(`/roles/${roleId}`, data);
    return response.data.data;
  },

  deleteRole: async (roleId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<void>>(`/roles/${roleId}`);
  },

  getRolePermissions: async (roleId: string): Promise<string[]> => {
    const response = await apiClient.get<ApiResponse<RolePermissionResponse>>(
      `/roles/${roleId}/permissions`
    );
    return response.data.data.permissions;
  },

  updateRolePermissions: async (roleId: string, permissions: string[]): Promise<string[]> => {
    const response = await apiClient.put<ApiResponse<RolePermissionResponse>>(
      `/roles/${roleId}/permissions`,
      { permissions } as RolePermissionUpdateRequest
    );
    return response.data.data.permissions;
  },

  // Role Assignments
  getRoleAssignments: async (membershipId?: string): Promise<UserRoleAssignment[]> => {
    const params = membershipId ? { membershipId } : undefined;
    const response = await apiClient.get<ApiResponse<UserRoleAssignment[]>>('/role-assignments', {
      params,
    });
    return response.data.data;
  },

  assignRole: async (data: CreateRoleAssignmentRequest): Promise<UserRoleAssignment> => {
    const response = await apiClient.post<ApiResponse<UserRoleAssignment>>('/role-assignments', data);
    return response.data.data;
  },

  revokeRoleAssignment: async (assignmentId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<void>>(`/role-assignments/${assignmentId}`);
  },

  // Member-Scoped Role Assignment Endpoints
  getMemberRoles: async (membershipId: string): Promise<UserRoleAssignment[]> => {
    const response = await apiClient.get<ApiResponse<UserRoleAssignment[]>>(
      `/members/${membershipId}/roles`
    );
    return response.data.data;
  },

  assignRoleToMember: async (
    membershipId: string,
    data: Omit<CreateRoleAssignmentRequest, 'membershipId'>
  ): Promise<UserRoleAssignment> => {
    const payload: CreateRoleAssignmentRequest = {
      ...data,
      membershipId,
    };
    const response = await apiClient.post<ApiResponse<UserRoleAssignment>>(
      `/members/${membershipId}/roles`,
      payload
    );
    return response.data.data;
  },

  revokeMemberRole: async (membershipId: string, assignmentId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<void>>(
      `/members/${membershipId}/roles/${assignmentId}`
    );
  },
};
