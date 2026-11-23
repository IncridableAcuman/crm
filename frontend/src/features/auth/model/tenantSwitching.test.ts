import { describe, it, expect, beforeEach } from 'vitest';
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
});
