import { describe, it, expect, vi, beforeEach } from 'vitest';
import { roleApi } from './roleApi';
import { apiClient } from '@/shared/api/client';
import {
  Role,
  CreateRoleRequest,
  UpdateRoleRequest,
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
    code: 'CASHIER',
    name: 'Cashier',
    description: 'Handles POS transactions',
    isSystem: true,
    isActive: true,
    permissions: ['sales.orders.create'],
    createdAt: '2026-11-24T12:00:00Z',
    updatedAt: '2026-11-24T12:00:00Z',
  };

  const mockAssignment: UserRoleAssignment = {
    id: 'assign-1',
    tenantId: 'tenant-1',
    membershipId: 'mem-1',
    roleId: 'role-1',
    roleCode: 'CASHIER',
    roleName: 'Cashier',
    scopeType: 'TENANT',
    branchId: null,
    isActive: true,
    createdAt: '2026-11-24T12:00:00Z',
    revokedAt: null,
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

  it('fetches a role by id', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: mockRole,
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const role = await roleApi.getRoleById('role-1');
    expect(apiClient.get).toHaveBeenCalledWith('/roles/role-1');
    expect(role.code).toBe('CASHIER');
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

  it('updates a custom role name and description', async () => {
    const updatePayload: UpdateRoleRequest = {
      name: 'Senior Supervisor',
      description: 'Updated description',
    };

    vi.mocked(apiClient.put).mockResolvedValueOnce({
      data: {
        success: true,
        data: { ...mockRole, id: 'role-2', isSystem: false, ...updatePayload },
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const updated = await roleApi.updateRole('role-2', updatePayload);
    expect(apiClient.put).toHaveBeenCalledWith('/roles/role-2', updatePayload);
    expect(updated.name).toBe('Senior Supervisor');
  });

  it('gets role permissions from RolePermissionResponse envelope', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          roleId: 'role-2',
          roleCode: 'SUPERVISOR',
          permissions: ['sales.orders.read', 'catalog.products.read'],
        },
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const perms = await roleApi.getRolePermissions('role-2');
    expect(apiClient.get).toHaveBeenCalledWith('/roles/role-2/permissions');
    expect(perms).toEqual(['sales.orders.read', 'catalog.products.read']);
  });

  it('updates role permissions and unwraps RolePermissionResponse', async () => {
    vi.mocked(apiClient.put).mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          roleId: 'role-2',
          roleCode: 'SUPERVISOR',
          permissions: ['sales.orders.create', 'catalog.products.read'],
        },
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const perms = await roleApi.updateRolePermissions('role-2', [
      'sales.orders.create',
      'catalog.products.read',
    ]);
    expect(apiClient.put).toHaveBeenCalledWith('/roles/role-2/permissions', {
      permissions: ['sales.orders.create', 'catalog.products.read'],
    });
    expect(perms).toEqual(['sales.orders.create', 'catalog.products.read']);
  });

  it('deletes or deactivates a custom role', async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce({
      data: { success: true },
    });

    await roleApi.deleteRole('role-2');
    expect(apiClient.delete).toHaveBeenCalledWith('/roles/role-2');
  });

  it('fetches role assignments with optional membershipId filter', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: [mockAssignment],
        timestamp: '2026-11-24T12:00:00Z',
      },
    });

    const assignments = await roleApi.getRoleAssignments('mem-1');
    expect(apiClient.get).toHaveBeenCalledWith('/role-assignments', {
      params: { membershipId: 'mem-1' },
    });
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
        data: {
          ...mockAssignment,
          id: 'assign-2',
          membershipId: 'mem-2',
          scopeType: 'BRANCH',
          branchId: 'branch-1',
        },
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

  it('supports member-scoped role endpoints', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { success: true, data: [mockAssignment], timestamp: '2026-11-24T12:00:00Z' },
    });
    const list = await roleApi.getMemberRoles('mem-1');
    expect(apiClient.get).toHaveBeenCalledWith('/members/mem-1/roles');
    expect(list).toHaveLength(1);

    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: { success: true, data: mockAssignment, timestamp: '2026-11-24T12:00:00Z' },
    });
    await roleApi.assignRoleToMember('mem-1', {
      roleId: 'role-1',
      scopeType: 'TENANT',
      branchId: null,
    });
    expect(apiClient.post).toHaveBeenCalledWith('/members/mem-1/roles', {
      membershipId: 'mem-1',
      roleId: 'role-1',
      scopeType: 'TENANT',
      branchId: null,
    });

    vi.mocked(apiClient.delete).mockResolvedValueOnce({ data: { success: true } });
    await roleApi.revokeMemberRole('mem-1', 'assign-1');
    expect(apiClient.delete).toHaveBeenCalledWith('/members/mem-1/roles/assign-1');
  });
});
