import { describe, it, expect, vi, beforeEach } from 'vitest';
import { roleApi } from './roleApi';
import { apiClient } from '@/shared/api/client';
import {
  Role,
  CreateRoleRequest,
  UserRoleAssignment,
  CreateRoleAssignmentRequest,
} from '../model/types';

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('roleApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockRole: Role = {
    id: 'role-1',
    tenantId: 'tenant-1',
    code: 'CASHIER',
    name: 'Cashier',
    description: 'Handles POS transactions',
    isSystem: true,
    isActive: true,
    permissions: ['sales.pos.checkout'],
    createdAt: '2026-11-24T12:00:00Z',
    updatedAt: '2026-11-24T12:00:00Z',
  };

  const mockAssignment: UserRoleAssignment = {
    id: 'assign-1',
    membershipId: 'mem-1',
    roleId: 'role-1',
    roleCode: 'CASHIER',
    roleName: 'Cashier',
    scopeType: 'TENANT',
    branchId: null,
    isActive: true,
    createdAt: '2026-11-24T12:00:00Z',
  };

  it('fetches roles for the active tenant', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: [mockRole],
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const roles = await roleApi.getRoles();
    expect(apiClient.get).toHaveBeenCalledWith('/roles');
    expect(roles).toHaveLength(1);
    expect(roles[0]?.code).toBe('CASHIER');
  });

  it('creates a custom role', async () => {
    const payload: CreateRoleRequest = {
      code: 'SUPERVISOR',
      name: 'Supervisor',
      permissions: ['identity.roles.read'],
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        success: true,
        data: { ...mockRole, id: 'role-2', code: 'SUPERVISOR', name: 'Supervisor', isSystem: false },
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const created = await roleApi.createRole(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/roles', payload);
    expect(created.code).toBe('SUPERVISOR');
    expect(created.isSystem).toBe(false);
  });

  it('updates role permissions', async () => {
    vi.mocked(apiClient.put).mockResolvedValueOnce({
      data: {
        success: true,
        data: ['sales.pos.checkout', 'catalog.products.read'],
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const perms = await roleApi.updateRolePermissions('role-2', ['sales.pos.checkout', 'catalog.products.read']);
    expect(apiClient.put).toHaveBeenCalledWith('/roles/role-2/permissions', {
      permissions: ['sales.pos.checkout', 'catalog.products.read'],
    });
    expect(perms).toHaveLength(2);
  });

  it('deletes or deactivates a custom role', async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce({
      data: { success: true },
    });

    await roleApi.deleteRole('role-2');
    expect(apiClient.delete).toHaveBeenCalledWith('/roles/role-2');
  });

  it('fetches role assignments', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: [mockAssignment],
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const assignments = await roleApi.getRoleAssignments();
    expect(apiClient.get).toHaveBeenCalledWith('/role-assignments', { params: undefined });
    expect(assignments).toHaveLength(1);
    expect(assignments[0]?.roleCode).toBe('CASHIER');
  });

  it('assigns a role to a member', async () => {
    const assignPayload: CreateRoleAssignmentRequest = {
      membershipId: 'mem-2',
      roleId: 'role-1',
      scopeType: 'BRANCH',
      branchId: 'branch-1',
    };

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        success: true,
        data: { ...mockAssignment, id: 'assign-2', membershipId: 'mem-2', scopeType: 'BRANCH', branchId: 'branch-1' },
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const assigned = await roleApi.assignRole(assignPayload);
    expect(apiClient.post).toHaveBeenCalledWith('/role-assignments', assignPayload);
    expect(assigned.id).toBe('assign-2');
    expect(assigned.scopeType).toBe('BRANCH');
  });

  it('revokes a role assignment', async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce({
      data: { success: true },
    });

    await roleApi.revokeRoleAssignment('assign-1');
    expect(apiClient.delete).toHaveBeenCalledWith('/role-assignments/assign-1');
  });
});
