import {
  apiClient,
  setSharedSessionState,
  clearSharedSessionState,
  broadcastAuthCleared,
  getSharedSessionState,
} from '../../../shared/api/client';
import { ApiResponse, AuthResponse } from '../../../entities/session/model/types';
import { useAuthStore } from '../model/useAuthStore';
import { queryClient } from '../../../shared/api/queryClient';

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
    setSharedSessionState({
      state: 'AUTHENTICATED',
      timestamp: Date.now(),
      refreshEpoch: 0,
    });
    return res.data.data;
  },

  login: async (payload: LoginPayload): Promise<AuthResponse> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/auth/login', payload);
    setSharedSessionState({
      state: 'AUTHENTICATED',
      timestamp: Date.now(),
      refreshEpoch: 0,
    });
    return res.data.data;
  },

  selectTenant: async (payload: SelectTenantPayload): Promise<AuthResponse> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/auth/select-tenant', payload);
    setSharedSessionState({
      state: 'AUTHENTICATED',
      timestamp: Date.now(),
      refreshEpoch: getSharedSessionState()?.refreshEpoch ?? 0,
    });
    return res.data.data;
  },

  refreshToken: async (): Promise<AuthResponse> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/auth/refresh');
    setSharedSessionState({
      state: 'AUTHENTICATED',
      timestamp: Date.now(),
      refreshEpoch: getSharedSessionState()?.refreshEpoch ?? 0,
    });
    return res.data.data;
  },

  logout: async (): Promise<void> => {
    try {
      await apiClient.post<ApiResponse<void>>('/auth/logout');
    } finally {
      await queryClient.cancelQueries();
      useAuthStore.getState().clearAuth();
      queryClient.clear();
      clearSharedSessionState();
      broadcastAuthCleared();
    }
  },

  getMe: async (): Promise<AuthResponse> => {
    const res = await apiClient.get<ApiResponse<AuthResponse>>('/auth/me');
    return res.data.data;
  },
};
