import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HomePage } from './HomePage';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import * as authQueries from '@/features/auth/model/authQueries';
import * as branchModule from '@/entities/branch';
import * as roleModule from '@/entities/role';
import * as systemModule from '@/entities/system';
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

vi.mock('@/features/auth/model/authQueries', () => ({
  useCurrentUser: vi.fn(),
}));

vi.mock('@/entities/branch', async () => {
  const actual = await vi.importActual<typeof import('@/entities/branch')>('@/entities/branch');
  return { ...actual, useBranches: vi.fn() };
});

vi.mock('@/entities/role', async () => {
  const actual = await vi.importActual<typeof import('@/entities/role')>('@/entities/role');
  return { ...actual, useRoles: vi.fn(), useRoleAssignments: vi.fn() };
});

vi.mock('@/entities/system', async () => {
  const actual = await vi.importActual<typeof import('@/entities/system')>('@/entities/system');
  return { ...actual, useSystemHealth: vi.fn() };
});

describe('HomePage Dashboard Data Integrity', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.getState().setActiveTenant({
      tenantId: 'tenant-1',
      tenantName: 'Test Store',
      tenantCode: 'TEST',
      membershipStatus: 'ACTIVE',
      current: true,
    });
    vi.mocked(authQueries.useCurrentUser).mockReturnValue({
      data: { user: { firstName: 'Ali', lastName: 'Valiev', email: 'ali@test.com' } },
    } as never);
    vi.mocked(branchModule.useBranches).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as never);
    vi.mocked(roleModule.useRoles).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as never);
    vi.mocked(roleModule.useRoleAssignments).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as never);
    vi.mocked(systemModule.useSystemHealth).mockReturnValue({
      data: {
        status: 'UP',
        system: 'SAVDOCRM',
        version: '0.0.1-SNAPSHOT',
        serverTime: '2026-11-26T08:00:00Z',
        framework: 'Spring Boot 4.2.0 / Java 21',
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);
  });

  it('does not show misleading zero values while permissions are loading', () => {
    vi.mocked(permissionQueries.useMyPermissions).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: vi.fn(),
    } as never);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(false);

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.getAllByText('...')).toHaveLength(4);
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it('disables role queries and displays Restricted + branch-scoped permissions for CASHIER', () => {
    vi.mocked(permissionQueries.useMyPermissions).mockReturnValue({
      data: {
        tenantId: 'tenant-1',
        membershipId: 'mem-1',
        tenantPermissions: [],
        branchPermissions: {
          'branch-1': ['catalog.products.read', 'sales.orders.create', 'tenancy.branches.read'],
        },
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as never);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(true);

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <HomePage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(roleModule.useRoles).toHaveBeenCalledWith({ enabled: false });
    expect(roleModule.useRoleAssignments).toHaveBeenCalledWith(undefined, { enabled: false });
    expect(screen.getAllByText('Restricted')).toHaveLength(2);
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('sales.orders.create')).toBeInTheDocument();
    expect(screen.getByText('SAVDOCRM API UP')).toBeInTheDocument();
  });
});
