import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RoleAssignmentsPage } from './RoleAssignmentsPage';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import * as roleModule from '@/entities/role';
import * as branchModule from '@/entities/branch';
import * as permissionQueries from '@/entities/permission/model/permissionQueries';

vi.mock('@/entities/permission/model/permissionQueries', async () => {
  const actual = await vi.importActual<typeof import('@/entities/permission/model/permissionQueries')>(
    '@/entities/permission/model/permissionQueries'
  );
  return {
    ...actual,
    useMyPermissions: vi.fn(),
    useHasPermission: vi.fn(),
    useHasAnyScopePermission: vi.fn(),
  };
});

vi.mock('@/entities/role', async () => {
  const actual = await vi.importActual<typeof import('@/entities/role')>('@/entities/role');
  return {
    ...actual,
    useRoleAssignments: vi.fn(),
    useRoles: vi.fn(),
    useAssignRole: vi.fn(),
    useRevokeRoleAssignment: vi.fn(),
  };
});

vi.mock('@/entities/branch', async () => {
  const actual = await vi.importActual<typeof import('@/entities/branch')>('@/entities/branch');
  return { ...actual, useBranches: vi.fn() };
});

describe('RoleAssignmentsPage Branch Scope & Owner Restrictions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.getState().setActiveTenant({
      tenantId: 'tenant-1',
      tenantName: 'Main Store',
      tenantCode: 'MAIN',
      membershipStatus: 'ACTIVE',
      current: true,
    });
    vi.mocked(roleModule.useRoleAssignments).mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as never);
    vi.mocked(roleModule.useRoles).mockReturnValue({
      data: [
        {
          id: 'role-owner',
          code: 'OWNER',
          name: 'Owner',
          isSystem: true,
          isActive: true,
          permissions: ['system.settings.manage'],
          createdAt: '',
          updatedAt: '',
        },
        {
          id: 'role-cashier',
          code: 'CASHIER',
          name: 'Cashier',
          isSystem: true,
          isActive: true,
          permissions: ['sales.orders.create'],
          createdAt: '',
          updatedAt: '',
        },
      ],
    } as never);
    vi.mocked(branchModule.useBranches).mockReturnValue({
      data: [
        {
          id: 'branch-1',
          tenantId: 'tenant-1',
          code: 'BR-01',
          name: 'Branch One',
          isActive: true,
          isMain: true,
          createdAt: '',
          updatedAt: '',
        },
      ],
    } as never);
    vi.mocked(roleModule.useAssignRole).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    vi.mocked(roleModule.useRevokeRoleAssignment).mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
    } as never);
    vi.mocked(permissionQueries.useMyPermissions).mockReturnValue({
      data: {
        tenantId: 'tenant-1',
        membershipId: 'm1',
        tenantPermissions: ['system.settings.manage', 'identity.roles.manage'],
        branchPermissions: {},
      },
    } as never);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(true);
  });

  it('clears branch selection when switching back to TENANT scope and locks OWNER to TENANT scope', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <RoleAssignmentsPage />
      </QueryClientProvider>
    );

    fireEvent.click(screen.getByRole('button', { name: /Assign Role/i }));

    // Select BRANCH scope and pick a branch
    const branchRadio = screen.getByRole('radio', { name: /BRANCH Scope/i });
    fireEvent.click(branchRadio);
    const branchSelect = screen.getByLabelText(/Select Branch/i) as HTMLSelectElement;
    fireEvent.change(branchSelect, { target: { value: 'branch-1' } });
    expect(branchSelect.value).toBe('branch-1');

    // Switch back to TENANT scope -> branch selector disappears & branchId resets
    const tenantRadio = screen.getByRole('radio', { name: /TENANT Scope/i });
    fireEvent.click(tenantRadio);
    expect(screen.queryByLabelText(/Select Branch/i)).not.toBeInTheDocument();

    // Switch back to BRANCH -> branchId was cleared to ''
    fireEvent.click(branchRadio);
    expect((screen.getByLabelText(/Select Branch/i) as HTMLSelectElement).value).toBe('');

    // Selecting OWNER forces TENANT scope and disables BRANCH radio
    const roleSelect = screen.getByLabelText(/Select Role/i);
    fireEvent.change(roleSelect, { target: { value: 'role-owner' } });
    expect(tenantRadio).toBeChecked();
    expect(branchRadio).toBeDisabled();
  });
});
