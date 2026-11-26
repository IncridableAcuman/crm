import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CategoriesPage } from './CategoriesPage';
import * as categoryModule from '@/entities/category';
import * as unitModule from '@/entities/unit';
import * as branchModule from '@/entities/branch';
import * as permissionQueries from '@/entities/permission/model/permissionQueries';

vi.mock('@/entities/permission/model/permissionQueries', async () => {
  const actual = await vi.importActual<typeof import('@/entities/permission/model/permissionQueries')>(
    '@/entities/permission/model/permissionQueries'
  );
  return {
    ...actual,
    useHasPermission: vi.fn(),
    useHasAnyScopePermission: vi.fn(),
    useCatalogReadBranchScope: vi.fn(),
  };
});

vi.mock('@/entities/category', async () => {
  const actual = await vi.importActual<typeof import('@/entities/category')>('@/entities/category');
  return {
    ...actual,
    useCategoryTree: vi.fn(),
    useCategories: vi.fn(),
    useCreateCategory: vi.fn(),
    useUpdateCategory: vi.fn(),
    useMoveCategory: vi.fn(),
    useUpdateCategoryStatus: vi.fn(),
    useDeleteCategory: vi.fn(),
  };
});

vi.mock('@/entities/unit', async () => {
  const actual = await vi.importActual<typeof import('@/entities/unit')>('@/entities/unit');
  return {
    ...actual,
    useUnits: vi.fn(),
    useCreateUnit: vi.fn(),
    useUpdateUnit: vi.fn(),
    useUpdateUnitStatus: vi.fn(),
    useDeleteUnit: vi.fn(),
  };
});

vi.mock('@/entities/branch', async () => {
  const actual = await vi.importActual<typeof import('@/entities/branch')>('@/entities/branch');
  return { ...actual, useBranches: vi.fn() };
});

describe('CategoriesPage & UnitManagerPanel', () => {
  const noopMutation = { mutateAsync: vi.fn(), isPending: false } as never;

  const flatCategories: categoryModule.Category[] = [
    {
      id: 'cat-root',
      parentId: null,
      code: 'BEV',
      name: 'Beverages',
      description: 'Drinks',
      depth: 0,
      sortOrder: 0,
      isActive: true,
      createdAt: '2026-11-26T00:00:00Z',
      updatedAt: '2026-11-26T00:00:00Z',
      version: 0,
    },
    {
      id: 'cat-child',
      parentId: 'cat-root',
      code: 'COLD',
      name: 'Cold Drinks',
      description: null,
      depth: 1,
      sortOrder: 0,
      isActive: true,
      createdAt: '2026-11-26T00:00:00Z',
      updatedAt: '2026-11-26T00:00:00Z',
      version: 0,
    },
    {
      id: 'cat-inactive',
      parentId: null,
      code: 'OLD_CAT',
      name: 'Archived Shelf',
      description: null,
      depth: 0,
      sortOrder: 1,
      isActive: false,
      createdAt: '2026-11-26T00:00:00Z',
      updatedAt: '2026-11-26T00:00:00Z',
      version: 0,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(true);
    vi.mocked(permissionQueries.useCatalogReadBranchScope).mockReturnValue({
      canRead: true,
      isTenantScoped: true,
      authorizedBranchIds: [],
      resolvedBranchId: undefined,
      invalidBranchRequested: false,
    });
    vi.mocked(branchModule.useBranches).mockReturnValue({ data: [] } as never);

    vi.mocked(categoryModule.useCategoryTree).mockReturnValue({
      data: [
        {
          ...flatCategories[0]!,
          children: [
            {
              ...flatCategories[1]!,
              children: [],
            },
          ],
        },
      ],
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never);

    vi.mocked(categoryModule.useCategories).mockReturnValue({ data: flatCategories } as never);
    vi.mocked(categoryModule.useCreateCategory).mockReturnValue(noopMutation);
    vi.mocked(categoryModule.useUpdateCategory).mockReturnValue(noopMutation);
    vi.mocked(categoryModule.useMoveCategory).mockReturnValue(noopMutation);
    vi.mocked(categoryModule.useUpdateCategoryStatus).mockReturnValue(noopMutation);
    vi.mocked(categoryModule.useDeleteCategory).mockReturnValue(noopMutation);

    vi.mocked(unitModule.useUnits).mockReturnValue({
      data: [
        {
          id: 'u-sys',
          code: 'PCS',
          name: 'Piece',
          symbol: 'pcs',
          allowDecimal: false,
          decimalPrecision: 0,
          isSystem: true,
          isActive: true,
          createdAt: '2026-11-26T00:00:00Z',
          updatedAt: '2026-11-26T00:00:00Z',
          version: 0,
        },
        {
          id: 'u-custom',
          code: 'TRAY',
          name: 'Tray',
          symbol: 'tr',
          allowDecimal: false,
          decimalPrecision: 0,
          isSystem: false,
          isActive: true,
          createdAt: '2026-11-26T00:00:00Z',
          updatedAt: '2026-11-26T00:00:00Z',
          version: 0,
        },
      ],
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never);
    vi.mocked(unitModule.useCreateUnit).mockReturnValue(noopMutation);
    vi.mocked(unitModule.useUpdateUnit).mockReturnValue(noopMutation);
    vi.mocked(unitModule.useUpdateUnitStatus).mockReturnValue(noopMutation);
    vi.mocked(unitModule.useDeleteUnit).mockReturnValue(noopMutation);
  });

  it('renders hierarchical category tree, filters inactive parents in Move dialog, and enforces system unit immutability', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <CategoriesPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.getByText('Beverages')).toBeInTheDocument();
    expect(screen.getByText('Cold Drinks')).toBeInTheDocument();

    // Open Move dialog for Beverages: neither Cold Drinks (descendant) nor Archived Shelf (inactive) may appear
    fireEvent.click(screen.getByLabelText('Move Beverages'));
    expect(screen.getByText('Move Category: Beverages')).toBeInTheDocument();
    expect(screen.queryByText(/Archived Shelf/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Cold Drinks \(COLD\)/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    // Switch to Units of Measure tab
    fireEvent.click(screen.getByRole('button', { name: /Units of Measure/i }));

    expect(screen.getByText('PCS')).toBeInTheDocument();
    expect(screen.getByText('Immutable')).toBeInTheDocument();
    expect(screen.getByText('TRAY')).toBeInTheDocument();
    expect(screen.getByLabelText('Edit unit TRAY')).toBeInTheDocument();
    expect(screen.queryByLabelText('Edit unit PCS')).not.toBeInTheDocument();
  });

  it('hides category and unit mutation controls for read-only users', () => {
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <CategoriesPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.queryByRole('button', { name: /New Category/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Edit Beverages')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Units of Measure/i }));
    expect(screen.queryByRole('button', { name: /New Custom Unit/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Edit unit TRAY')).not.toBeInTheDocument();
  });
});
