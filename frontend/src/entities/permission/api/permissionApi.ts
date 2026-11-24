import { apiClient } from '@/shared/api/client';
import { ApiResponse } from '@/entities/session/model/types';
import { Permission, UserPermissionsResponse } from '../model/types';

export const permissionApi = {
  getMyPermissions: async (): Promise<UserPermissionsResponse> => {
    const response = await apiClient.get<ApiResponse<UserPermissionsResponse>>('/me/permissions');
    return response.data.data;
  },

  getAllPermissions: async (): Promise<Permission[]> => {
    const response = await apiClient.get<ApiResponse<Permission[]>>('/permissions');
    return response.data.data;
  },
};
