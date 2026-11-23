import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { User, TenantMembership } from '../../../entities/user/model/types';
import { AuthResponse } from '../../../entities/session/model/types';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  activeTenant: TenantMembership | null;
  availableTenants: TenantMembership[];
  isAuthenticated: boolean;
  isTenantSelected: boolean;

  setAuth: (response: AuthResponse) => void;
  setTokens: (accessToken: string, refreshToken: string) => void;
  setActiveTenant: (tenant: TenantMembership, accessToken: string, refreshToken: string) => void;
  setAvailableTenants: (tenants: TenantMembership[]) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      activeTenant: null,
      availableTenants: [],
      isAuthenticated: false,
      isTenantSelected: false,

      setAuth: (response: AuthResponse) => {
        set({
          user: response.user,
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          activeTenant: response.activeTenant || null,
          availableTenants: response.availableTenants || [],
          isAuthenticated: !!response.accessToken,
          isTenantSelected: !!response.activeTenant,
        });
      },

      setTokens: (accessToken: string, refreshToken: string) => {
        set({
          accessToken,
          refreshToken,
          isAuthenticated: true,
        });
      },

      setActiveTenant: (tenant: TenantMembership, accessToken: string, refreshToken: string) => {
        set((state) => ({
          activeTenant: tenant,
          accessToken,
          refreshToken,
          isTenantSelected: true,
          availableTenants: state.availableTenants.map((t) => ({
            ...t,
            current: t.tenantId === tenant.tenantId,
          })),
        }));
      },

      setAvailableTenants: (availableTenants: TenantMembership[]) => {
        set({ availableTenants });
      },

      clearAuth: () => {
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          activeTenant: null,
          availableTenants: [],
          isAuthenticated: false,
          isTenantSelected: false,
        });
      },
    }),
    {
      name: 'savdocrm_auth',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        user: state.user,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        activeTenant: state.activeTenant,
        availableTenants: state.availableTenants,
        isAuthenticated: state.isAuthenticated,
        isTenantSelected: state.isTenantSelected,
      }),
    }
  )
);
