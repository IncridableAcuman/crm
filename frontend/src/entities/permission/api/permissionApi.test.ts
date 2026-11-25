import { describe, it, expect, vi, beforeEach } from 'vitest';
import { permissionApi } from './permissionApi';
import { apiClient } from '@/shared/api/client';
import { Permission, UserPermissionsResponse } from '../model/types';

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('permissionApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches effective tenant and branch permissions from /me/permissions', async () => {
    const payload: UserPermissionsResponse = {
      tenantId: 'tenant-1',
      membershipId: 'mem-1',
      tenantPermissions: ['tenancy.branches.read', 'identity.roles.read'],
      branchPermissions: {
        'branch-1': ['sales.orders.create'],
      },
    };

    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: payload,
        timestamp: '2026-11-25T00:00:00Z',
      },
    });

    const result = await permissionApi.getMyPermissions();
    expect(apiClient.get).toHaveBeenCalledWith('/me/permissions');
    expect(result.tenantPermissions).toContain('tenancy.branches.read');
    expect(result.branchPermissions['branch-1']).toEqual(['sales.orders.create']);
  });

  it('fetches canonical permission catalog from /permissions', async () => {
    const mockPerm: Permission = {
      id: 'perm-1',
      code: 'tenancy.branches.read',
      module: 'tenancy',
      resource: 'branches',
      action: 'read',
      description: 'View store branch details and locations',
    };

    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: [mockPerm],
        timestamp: '2026-11-25T00:00:00Z',
      },
    });

    const result = await permissionApi.getAllPermissions();
    expect(apiClient.get).toHaveBeenCalledWith('/permissions');
    expect(result).toHaveLength(1);
    expect(result[0]?.resource).toBe('branches');
    expect(result[0]?.action).toBe('read');
  });
});
