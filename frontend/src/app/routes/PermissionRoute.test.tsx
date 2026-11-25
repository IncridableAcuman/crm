import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { PermissionRoute } from './PermissionRoute';
import * as permissionQueries from '@/entities/permission';
import { permissionApi } from '@/entities/permission/api/permissionApi';
import { getPermissionLabel, getModuleLabel } from '@/entities/permission/model/permissionLabels';

vi.mock('@/entities/permission', async () => {
  const actual = await vi.importActual<typeof import('@/entities/permission')>('@/entities/permission');
  return {
    ...actual,
    useMyPermissions: vi.fn(),
    useHasPermission: vi.fn(),
    useHasAnyScopePermission: vi.fn(),
  };
});

describe('PermissionRoute Fail-Closed Authorization', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderRoute = (allowBranchScope = false) =>
    render(
      <MemoryRouter initialEntries={['/roles']}>
        <Routes>
          <Route element={<PermissionRoute permission="identity.roles.read" allowBranchScope={allowBranchScope} />}>
            <Route path="/roles" element={<div data-testid="protected-content">Roles Content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    );

  it('does not mount protected route while permissions are loading', () => {
    vi.mocked(permissionQueries.useMyPermissions).mockReturnValue({
      isLoading: true,
      isError: false,
      isFetching: true,
      refetch: vi.fn(),
    } as never);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(false);

    renderRoute();

    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Verifying permissions');
  });

  it('fails closed and shows Retry on permissions API failure', () => {
    const refetch = vi.fn();
    vi.mocked(permissionQueries.useMyPermissions).mockReturnValue({
      isLoading: false,
      isError: true,
      isFetching: false,
      refetch,
    } as never);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(false);

    renderRoute();

    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
    expect(screen.getByText('Unable to Verify Permissions')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it('blocks access with Access Denied when user lacks required permission', () => {
    vi.mocked(permissionQueries.useMyPermissions).mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    } as never);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(false);

    renderRoute();

    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
    expect(screen.getByText('Access Denied')).toBeInTheDocument();
  });

  it('renders protected route only when permissions loaded and authorized', () => {
    vi.mocked(permissionQueries.useMyPermissions).mockReturnValue({
      isLoading: false,
      isError: false,
      isFetching: false,
      refetch: vi.fn(),
    } as never);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(true);

    renderRoute();

    expect(screen.getByTestId('protected-content')).toBeInTheDocument();
  });
});

describe('Permission Catalog Labels & API Integration', () => {
  const CANONICAL_33 = [
    'analytics.dashboard.read',
    'analytics.reports.read',
    'catalog.products.read',
    'catalog.products.create',
    'catalog.products.update',
    'catalog.products.delete',
    'catalog.categories.read',
    'catalog.categories.manage',
    'inventory.stock.read',
    'inventory.adjustments.create',
    'inventory.transfers.create',
    'sales.orders.read',
    'sales.orders.create',
    'sales.refunds.create',
    'purchases.orders.read',
    'purchases.orders.create',
    'purchases.receipts.create',
    'parties.customers.read',
    'parties.customers.manage',
    'parties.suppliers.read',
    'parties.suppliers.manage',
    'finance.accounts.read',
    'finance.expenses.read',
    'finance.expenses.create',
    'tenancy.branches.read',
    'tenancy.branches.manage',
    'identity.users.read',
    'identity.users.manage',
    'identity.roles.read',
    'identity.roles.manage',
    'system.audit_logs.read',
    'system.settings.read',
    'system.settings.manage',
  ];

  it('provides non-empty readable labels for all 33 canonical permissions and safe fallback for unknown future codes', () => {
    expect(CANONICAL_33).toHaveLength(33);
    for (const code of CANONICAL_33) {
      const label = getPermissionLabel({ code });
      expect(label).toBeTruthy();
      expect(label).not.toBe(code);
    }

    // Future unknown permission fallback
    expect(
      getPermissionLabel({
        code: 'loyalty.points.redeem',
        resource: 'points',
        action: 'redeem',
      })
    ).toBe('Redeem Points');

    expect(getModuleLabel('tenancy')).toBe('Store & Branches');
    expect(getModuleLabel('loyalty')).toBe('Loyalty');
  });

  it('calls /permissions and /me/permissions correctly via permissionApi', async () => {
    expect(typeof permissionApi.getAllPermissions).toBe('function');
    expect(typeof permissionApi.getMyPermissions).toBe('function');
  });
});
