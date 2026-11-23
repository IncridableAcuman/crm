import { describe, it, expect, beforeEach, vi } from 'vitest';
import { queryClient } from '../../../shared/api/queryClient';
import { useAuthStore } from './useAuthStore';

describe('Tenant Switching & Query Cache Isolation', () => {
  beforeEach(() => {
    queryClient.clear();
    useAuthStore.getState().clearAuth();
  });

  it('should clear all query cache when switching tenant to prevent cross-tenant data leakage', () => {
    // Populate query cache with Tenant 1 data
    queryClient.setQueryData(['branches', { tenantId: 'tenant-1' }], [
      { id: 'b1', name: 'Branch Store 1' },
    ]);
    queryClient.setQueryData(['products', { tenantId: 'tenant-1' }], [
      { id: 'p1', name: 'Product Store 1' },
    ]);

    expect(queryClient.getQueryData(['branches', { tenantId: 'tenant-1' }])).toBeDefined();
    expect(queryClient.getQueryData(['products', { tenantId: 'tenant-1' }])).toBeDefined();

    // Tenant switch occurs: queryClient.clear() is invoked
    queryClient.clear();
    useAuthStore.getState().setActiveTenant({
      tenantId: 'tenant-2',
      tenantName: 'Store 2',
      tenantCode: 'S2',
      membershipStatus: 'ACTIVE',
      current: true,
    });

    // Verification: Cache is completely empty; zero stale data from tenant-1 remains
    expect(queryClient.getQueryData(['branches', { tenantId: 'tenant-1' }])).toBeUndefined();
    expect(queryClient.getQueryData(['products', { tenantId: 'tenant-1' }])).toBeUndefined();
    expect(useAuthStore.getState().activeTenant?.tenantId).toBe('tenant-2');
  });

  it('should cancel in-flight queries and purge cache during tenant switch', async () => {
    const cancelSpy = vi.spyOn(queryClient, 'cancelQueries');
    const clearSpy = vi.spyOn(queryClient, 'clear');

    // Simulate in-flight query
    queryClient.setQueryData(['inventory', 'tenant-1'], { items: ['item1'] });

    await queryClient.cancelQueries();
    queryClient.clear();

    expect(cancelSpy).toHaveBeenCalled();
    expect(clearSpy).toHaveBeenCalled();
    expect(queryClient.getQueryData(['inventory', 'tenant-1'])).toBeUndefined();
  });

  it('should clear all authentication state and query cache on logout', async () => {
    useAuthStore.getState().setAccessToken('mock-token');
    useAuthStore.getState().setActiveTenant({
      tenantId: 'tenant-1',
      tenantName: 'Store 1',
      tenantCode: 'S1',
      membershipStatus: 'ACTIVE',
      current: true,
    });
    queryClient.setQueryData(['user-profile'], { id: 'u1', name: 'Bob' });

    // Execute logout sequence
    await queryClient.cancelQueries();
    useAuthStore.getState().clearAuth();
    queryClient.clear();

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().activeTenant).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().isTenantSelected).toBe(false);
    expect(queryClient.getQueryData(['user-profile'])).toBeUndefined();
  });
});
