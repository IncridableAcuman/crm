import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from './useAuthStore';
import { AuthResponse } from '../../../entities/session/model/types';

describe('useAuthStore', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth();
  });

  it('should initialize with unauthenticated state', () => {
    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.isTenantSelected).toBe(false);
    expect(state.user).toBeNull();
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.activeTenant).toBeNull();
  });

  it('should set auth state correctly upon receiving AuthResponse', () => {
    const mockAuthResponse: AuthResponse = {
      accessToken: 'mock-access-token',
      refreshToken: 'mock-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: '11111111-1111-1111-1111-111111111111',
        email: 'test@savdocrm.com',
        username: 'testuser',
        firstName: 'Test',
        lastName: 'User',
      },
      activeTenant: {
        tenantId: '22222222-2222-2222-2222-222222222222',
        tenantName: 'Test Store',
        tenantCode: 'TESTSTORE',
        membershipStatus: 'ACTIVE',
        current: true,
      },
      availableTenants: [
        {
          tenantId: '22222222-2222-2222-2222-222222222222',
          tenantName: 'Test Store',
          tenantCode: 'TESTSTORE',
          membershipStatus: 'ACTIVE',
          current: true,
        },
      ],
    };

    useAuthStore.getState().setAuth(mockAuthResponse);

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.isTenantSelected).toBe(true);
    expect(state.accessToken).toBe('mock-access-token');
    expect(state.user?.email).toBe('test@savdocrm.com');
    expect(state.activeTenant?.tenantName).toBe('Test Store');
  });

  it('should switch active tenant correctly', () => {
    const initialTenant = {
      tenantId: 't1',
      tenantName: 'Store 1',
      tenantCode: 'S1',
      membershipStatus: 'ACTIVE' as const,
      current: true,
    };
    const secondTenant = {
      tenantId: 't2',
      tenantName: 'Store 2',
      tenantCode: 'S2',
      membershipStatus: 'ACTIVE' as const,
      current: false,
    };

    useAuthStore.setState({
      availableTenants: [initialTenant, secondTenant],
      activeTenant: initialTenant,
      isAuthenticated: true,
    });

    useAuthStore.getState().setActiveTenant(secondTenant, 'new-access-token', 'new-refresh-token');

    const state = useAuthStore.getState();
    expect(state.activeTenant?.tenantId).toBe('t2');
    expect(state.accessToken).toBe('new-access-token');
    expect(state.isTenantSelected).toBe(true);
    expect(state.availableTenants.find((t) => t.tenantId === 't2')?.current).toBe(true);
    expect(state.availableTenants.find((t) => t.tenantId === 't1')?.current).toBe(false);
  });

  it('should reset all state on clearAuth', () => {
    useAuthStore.setState({
      accessToken: 'token',
      isAuthenticated: true,
      isTenantSelected: true,
    });

    useAuthStore.getState().clearAuth();

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.isTenantSelected).toBe(false);
    expect(state.accessToken).toBeNull();
  });
});
