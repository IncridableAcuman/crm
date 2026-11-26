import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BranchesPage } from './BranchesPage';
import * as branchModule from '@/entities/branch';
import * as permissionQueries from '@/entities/permission/model/permissionQueries';

vi.mock('@/entities/permission/model/permissionQueries', async () => {
  const actual = await vi.importActual<typeof import('@/entities/permission/model/permissionQueries')>(
    '@/entities/permission/model/permissionQueries'
  );
  return {
    ...actual,
    useHasPermission: vi.fn(),
  };
});

vi.mock('@/entities/branch', async () => {
  const actual = await vi.importActual<typeof import('@/entities/branch')>('@/entities/branch');
  return {
    ...actual,
    useBranches: vi.fn(),
    useBranch: vi.fn(),
    useCreateBranch: vi.fn(),
  };
});

const SAMPLE_BRANCH: branchModule.Branch = {
  id: 'branch-1',
  tenantId: 'tenant-1',
  code: 'BR-01',
  name: 'Chorsu Branch',
  phone: '+998901234567',
  address: 'Navoi St. 24, Tashkent',
  isActive: true,
  isMain: true,
  createdAt: '2026-11-24T12:00:00Z',
  updatedAt: '2026-11-25T14:30:00Z',
};

describe('BranchesPage', () => {
  const createMutateAsync = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);
    vi.mocked(branchModule.useBranches).mockReturnValue({
      data: [SAMPLE_BRANCH],
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    } as never);
    vi.mocked(branchModule.useBranch).mockImplementation((id: string) => ({
      data: id === 'branch-1' ? SAMPLE_BRANCH : undefined,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    }) as never);
    vi.mocked(branchModule.useCreateBranch).mockReturnValue({
      mutateAsync: createMutateAsync,
      isPending: false,
    } as never);
  });

  it('renders branches and opens live branch details dialog via useBranch', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <BranchesPage />
      </QueryClientProvider>
    );

    expect(screen.getByText('Chorsu Branch')).toBeInTheDocument();
    expect(screen.getByText('BR-01')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('View branch Chorsu Branch'));

    await waitFor(() => {
      expect(branchModule.useBranch).toHaveBeenCalledWith('branch-1', { enabled: true });
      expect(screen.getByText('Branch Details')).toBeInTheDocument();
      expect(screen.getByText('branch-1')).toBeInTheDocument();
    });
  });

  it('submits create branch form matching CreateBranchCommand', async () => {
    createMutateAsync.mockResolvedValueOnce(SAMPLE_BRANCH);

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <BranchesPage />
      </QueryClientProvider>
    );

    fireEvent.click(screen.getByRole('button', { name: /New Branch/i }));
    fireEvent.change(screen.getByLabelText(/Branch Code/i), { target: { value: 'br-02' } });
    fireEvent.change(screen.getByLabelText(/Branch Name/i), { target: { value: 'Yunusobod' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Branch' }));

    await waitFor(() => {
      expect(createMutateAsync).toHaveBeenCalledWith({
        code: 'BR-02',
        name: 'Yunusobod',
        phone: null,
        address: null,
        isMain: false,
      });
    });
  });
});
