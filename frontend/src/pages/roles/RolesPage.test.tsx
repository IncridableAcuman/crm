import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RolesPage } from './RolesPage';
import * as roleModule from '@/entities/role';
import * as permissionQueries from '@/entities/permission/model/permissionQueries';
import type { Permission } from '@/entities/permission';

vi.mock('@/entities/permission/model/permissionQueries', async () => {
  const actual = await vi.importActual<typeof import('@/entities/permission/model/permissionQueries')>(
    '@/entities/permission/model/permissionQueries'
  );
  return {
    ...actual,
    usePermissionsCatalog: vi.fn(),
    useMyPermissions: vi.fn(),
    useHasPermission: vi.fn(),
  };
});

vi.mock('@/entities/role', async () => {
  const actual = await vi.importActual<typeof import('@/entities/role')>('@/entities/role');
  return {
    ...actual,
    useRoles: vi.fn(),
    useCreateRole: vi.fn(),
    useUpdateRole: vi.fn(),
    useUpdateRolePermissions: vi.fn(),
    useDeleteRole: vi.fn(),
  };
});

const SEVEN_SYSTEM_ROLES: roleModule.Role[] = [
  { code: 'OWNER', name: 'Owner' },
  { code: 'ADMIN', name: 'Administrator' },
  { code: 'MANAGER', name: 'Store Manager' },
  { code: 'CASHIER', name: 'Cashier' },
  { code: 'WAREHOUSE_MANAGER', name: 'Warehouse Manager' },
  { code: 'ACCOUNTANT', name: 'Accountant' },
  { code: 'VIEWER', name: 'Viewer' },
].map(({ code, name }, idx) => ({
  id: `sys-${idx}`,
  code,
  name,
  description: `System ${code}`,
  isSystem: true,
  isActive: true,
  permissions: code === 'OWNER' ? Array.from({ length: 33 }, (_, i) => `perm.${i}.read`) : ['tenancy.branches.read'],
  createdAt: '2026-11-25T00:00:00Z',
  updatedAt: '2026-11-25T00:00:00Z',
}));

const CUSTOM_ROLE: roleModule.Role = {
  id: 'custom-1',
  code: 'SHIFT_LEAD',
  name: 'Shift Lead',
  description: 'Custom shift supervisor',
  isSystem: false,
  isActive: true,
  permissions: ['tenancy.branches.read', 'sales.orders.read'],
  createdAt: '2026-11-25T00:00:00Z',
  updatedAt: '2026-11-25T00:00:00Z',
};

const CATALOG_33: Permission[] = [
  ['analytics', 'dashboard', 'read'],
  ['analytics', 'reports', 'read'],
  ['catalog', 'products', 'read'],
  ['catalog', 'products', 'create'],
  ['catalog', 'products', 'update'],
  ['catalog', 'products', 'delete'],
  ['catalog', 'categories', 'read'],
  ['catalog', 'categories', 'manage'],
  ['inventory', 'stock', 'read'],
  ['inventory', 'adjustments', 'create'],
  ['inventory', 'transfers', 'create'],
  ['sales', 'orders', 'read'],
  ['sales', 'orders', 'create'],
  ['sales', 'refunds', 'create'],
  ['purchases', 'orders', 'read'],
  ['purchases', 'orders', 'create'],
  ['purchases', 'receipts', 'create'],
  ['parties', 'customers', 'read'],
  ['parties', 'customers', 'manage'],
  ['parties', 'suppliers', 'read'],
  ['parties', 'suppliers', 'manage'],
  ['finance', 'accounts', 'read'],
  ['finance', 'expenses', 'read'],
  ['finance', 'expenses', 'create'],
  ['tenancy', 'branches', 'read'],
  ['tenancy', 'branches', 'manage'],
  ['identity', 'users', 'read'],
  ['identity', 'users', 'manage'],
  ['identity', 'roles', 'read'],
  ['identity', 'roles', 'manage'],
  ['system', 'audit_logs', 'read'],
  ['system', 'settings', 'read'],
  ['system', 'settings', 'manage'],
].map(([module, resource, action], i) => ({
  id: `p-${i}`,
  code: `${module}.${resource}.${action}`,
  module: module!,
  resource: resource!,
  action: action!,
  description: `Desc for ${module}.${resource}.${action}`,
}));

describe('RolesPage Audit Verification', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(roleModule.useRoles).mockReturnValue({
      data: [...SEVEN_SYSTEM_ROLES, CUSTOM_ROLE],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as never);
    vi.mocked(roleModule.useCreateRole).mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as never);
    vi.mocked(roleModule.useUpdateRole).mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as never);
    vi.mocked(roleModule.useUpdateRolePermissions).mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as never);
    vi.mocked(roleModule.useDeleteRole).mockReturnValue({ mutateAsync: vi.fn(), isPending: false } as never);

    vi.mocked(permissionQueries.usePermissionsCatalog).mockReturnValue({
      data: CATALOG_33,
      isLoading: false,
      error: null,
    } as never);
    vi.mocked(permissionQueries.useMyPermissions).mockReturnValue({
      data: {
        tenantId: 't1',
        membershipId: 'm1',
        tenantPermissions: CATALOG_33.map((p) => p.code),
        branchPermissions: {},
      },
    } as never);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);
  });

  it('displays all seven system roles and custom roles without 21/21 hardcoding', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <RolesPage />
      </QueryClientProvider>
    );

    for (const role of SEVEN_SYSTEM_ROLES) {
      expect(screen.getByText(role.code)).toBeInTheDocument();
    }
    expect(screen.getByText('SHIFT_LEAD')).toBeInTheDocument();
    expect(screen.getByText('All (33/33)')).toBeInTheDocument();
    expect(screen.queryByText(/21\/21/)).not.toBeInTheDocument();
  });

  it('protects system roles from edit/delete and opens permissions strictly read-only', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <RolesPage />
      </QueryClientProvider>
    );

    // Edit & Delete buttons exist only for the 1 custom role, never for the 7 system roles
    expect(screen.getAllByRole('button', { name: /Edit role/i })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: /Delete role/i })).toHaveLength(1);

    // Open OWNER permissions dialog
    const viewButtons = screen.getAllByRole('button', { name: 'View Permissions' });
    expect(viewButtons).toHaveLength(7);
    fireEvent.click(viewButtons[0]!);

    expect(screen.getByText(/System roles are strictly immutable/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save Permissions' })).not.toBeInTheDocument();

    // All 33 permissions are rendered with readable labels
    expect(screen.getByText('Manage Store Settings')).toBeInTheDocument();
    expect(screen.getByText('system.settings.manage')).toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')).toHaveLength(33);
    for (const cb of screen.getAllByRole('checkbox')) {
      expect(cb).toBeDisabled();
    }
  });
});
