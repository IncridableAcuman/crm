import { apiClient } from '../../../shared/api/client';
import { ApiResponse, AuthResponse } from '../../../entities/session/model/types';

export interface RegisterPayload {
  email: string;
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  storeName?: string;
}

export interface LoginPayload {
  emailOrUsername: string;
  password: string;
}

export interface SelectTenantPayload {
  tenantId: string;
}

export const authApi = {
  register: async (payload: RegisterPayload): Promise<AuthResponse> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/auth/register', payload);
    return res.data.data;
  },

  login: async (payload: LoginPayload): Promise<AuthResponse> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/auth/login', payload);
    return res.data.data;
  },

  selectTenant: async (payload: SelectTenantPayload): Promise<AuthResponse> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/auth/select-tenant', payload);
    return res.data.data;
  },

  refreshToken: async (refreshToken: string): Promise<AuthResponse> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/auth/refresh', { refreshToken });
    return res.data.data;
  },

  logout: async (refreshToken?: string): Promise<void> => {
    await apiClient.post<ApiResponse<void>>('/auth/logout', { refreshToken });
  },

  getMe: async (): Promise<AuthResponse> => {
    const res = await apiClient.get<ApiResponse<AuthResponse>>('/auth/me');
    return res.data.data;
  },
};
