import { create } from 'zustand';
import { TenantMembership } from '../../../entities/user/model/types';

interface AuthState {
  accessToken: string | null;
  activeTenant: TenantMembership | null;
  isAuthenticated: boolean;
  isTenantSelected: boolean;
  isInitialized: boolean;

  setAccessToken: (token: string | null) => void;
  setActiveTenant: (tenant: TenantMembership | null) => void;
  setInitialized: (initialized: boolean) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  activeTenant: null,
  isAuthenticated: false,
  isTenantSelected: false,
  isInitialized: false,

  setAccessToken: (token: string | null) => {
    set({
      accessToken: token,
      isAuthenticated: !!token,
    });
  },

  setActiveTenant: (tenant: TenantMembership | null) => {
    set({
      activeTenant: tenant,
      isTenantSelected: !!tenant,
    });
  },

  setInitialized: (isInitialized: boolean) => {
    set({ isInitialized });
  },

  clearAuth: () => {
    set({
      accessToken: null,
      activeTenant: null,
      isAuthenticated: false,
      isTenantSelected: false,
      isInitialized: true,
    });
  },
}));
