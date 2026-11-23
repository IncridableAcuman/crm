import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from './useAuthStore';
import { TenantMembership } from '../../../entities/user/model/types';

describe('useAuthStore (In-Memory Session Architecture)', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    useAuthStore.getState().clearAuth();
  });

  it('should initialize with unauthenticated and non-initialized state', () => {
    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.isTenantSelected).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.activeTenant).toBeNull();
    expect(state.isInitialized).toBe(true); // clearAuth sets isInitialized to true
  });

  it('should store access token in memory only and not in localStorage', () => {
    useAuthStore.getState().setAccessToken('in-memory-jwt-token-xyz');

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(true);
    expect(state.accessToken).toBe('in-memory-jwt-token-xyz');

    // Security Verification: Neither access token nor refresh token is in localStorage
    expect(localStorage.getItem('savdocrm_auth')).toBeNull();
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(sessionStorage.getItem('refreshToken')).toBeNull();
  });

  it('should set active tenant correctly', () => {
    const store: TenantMembership = {
      tenantId: '11111111-2222-3333-4444-555555555555',
      tenantName: 'Downtown Supermarket',
      tenantCode: 'DOWNTOWN',
      membershipStatus: 'ACTIVE',
      current: true,
    };

    useAuthStore.getState().setActiveTenant(store);

    const state = useAuthStore.getState();
    expect(state.isTenantSelected).toBe(true);
    expect(state.activeTenant?.tenantName).toBe('Downtown Supermarket');
    expect(state.activeTenant?.tenantId).toBe('11111111-2222-3333-4444-555555555555');
  });

  it('should reset all state on clearAuth', () => {
    useAuthStore.getState().setAccessToken('token-123');
    useAuthStore.getState().setActiveTenant({
      tenantId: 'tenant-1',
      tenantName: 'Store 1',
      tenantCode: 'S1',
      membershipStatus: 'ACTIVE',
      current: true,
    });

    useAuthStore.getState().clearAuth();

    const state = useAuthStore.getState();
    expect(state.isAuthenticated).toBe(false);
    expect(state.isTenantSelected).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.activeTenant).toBeNull();
  });
});
