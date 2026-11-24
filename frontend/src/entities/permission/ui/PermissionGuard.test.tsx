import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PermissionGuard } from './PermissionGuard';
import * as permissionQueries from '../model/permissionQueries';

vi.mock('../model/permissionQueries', () => ({
  useHasPermission: vi.fn(),
}));

describe('PermissionGuard Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders children when the user has the required permission', () => {
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);

    render(
      <PermissionGuard permission="identity.roles.manage">
        <button>Create Role</button>
      </PermissionGuard>
    );

    expect(screen.getByRole('button', { name: 'Create Role' })).toBeInTheDocument();
  });

  it('renders nothing when the user lacks permission and no fallback is provided', () => {
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);

    render(
      <PermissionGuard permission="identity.roles.manage">
        <button>Create Role</button>
      </PermissionGuard>
    );

    expect(screen.queryByRole('button', { name: 'Create Role' })).not.toBeInTheDocument();
  });

  it('renders fallback content when the user lacks permission', () => {
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);

    render(
      <PermissionGuard
        permission="identity.roles.manage"
        fallback={<p data-testid="fallback">No Access</p>}
      >
        <button>Create Role</button>
      </PermissionGuard>
    );

    expect(screen.queryByRole('button', { name: 'Create Role' })).not.toBeInTheDocument();
    expect(screen.getByTestId('fallback')).toHaveTextContent('No Access');
  });

  it('passes branchId to useHasPermission for branch-scoped checks', () => {
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);

    render(
      <PermissionGuard
        permission="sales.pos.checkout"
        branchId="branch-123"
      >
        <span>POS Terminal</span>
      </PermissionGuard>
    );

    expect(permissionQueries.useHasPermission).toHaveBeenCalledWith('sales.pos.checkout', 'branch-123');
    expect(screen.getByText('POS Terminal')).toBeInTheDocument();
  });
});
