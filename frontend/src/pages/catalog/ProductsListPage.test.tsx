import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProductsListPage } from './ProductsListPage';
import * as productModule from '@/entities/product';
import * as categoryModule from '@/entities/category';
import * as unitModule from '@/entities/unit';
import * as branchModule from '@/entities/branch';
import * as permissionQueries from '@/entities/permission/model/permissionQueries';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('@/entities/permission/model/permissionQueries', async () => {
  const actual = await vi.importActual<typeof import('@/entities/permission/model/permissionQueries')>(
    '@/entities/permission/model/permissionQueries'
  );
  return {
    ...actual,
    useMyPermissions: vi.fn(),
    useHasPermission: vi.fn(),
    useHasAnyScopePermission: vi.fn(),
    useCatalogReadBranchScope: vi.fn(),
  };
});

vi.mock('@/entities/product', async () => {
  const actual = await vi.importActual<typeof import('@/entities/product')>('@/entities/product');
  return {
    ...actual,
    useProducts: vi.fn(),
    useArchiveProduct: vi.fn(),
    useRestoreProduct: vi.fn(),
    useDeleteDraftProduct: vi.fn(),
  };
});

vi.mock('@/entities/category', async () => {
  const actual = await vi.importActual<typeof import('@/entities/category')>('@/entities/category');
  return { ...actual, useCategories: vi.fn() };
});

vi.mock('@/entities/unit', async () => {
  const actual = await vi.importActual<typeof import('@/entities/unit')>('@/entities/unit');
  return { ...actual, useUnits: vi.fn() };
});

vi.mock('@/entities/branch', async () => {
  const actual = await vi.importActual<typeof import('@/entities/branch')>('@/entities/branch');
  return { ...actual, useBranches: vi.fn() };
});

const SAMPLE_PRODUCTS: productModule.ProductSummary[] = [
  {
    id: 'prod-1',
    name: 'Plain Cotton Tee',
    brand: 'BasicCo',
    productType: 'SIMPLE',
    status: 'ACTIVE',
    categoryId: 'cat-1',
    categoryName: 'Apparel',
    unitId: 'u-1',
    unitCode: 'PCS',
    unitSymbol: 'pcs',
    variantCount: 1,
    defaultSku: 'TEE-001',
    primaryBarcode: '4601000000011',
    minSellingPrice: 12.5,
    maxSellingPrice: 12.5,
    createdAt: '2026-11-25T10:00:00Z',
    updatedAt: '2026-11-25T10:00:00Z',
    version: 0,
  },
  {
    id: 'prod-2',
    name: 'Archived Cap',
    brand: null,
    productType: 'SIMPLE',
    status: 'ARCHIVED',
    categoryId: null,
    categoryName: null,
    unitId: 'u-1',
    unitCode: 'PCS',
    unitSymbol: 'pcs',
    variantCount: 1,
    defaultSku: 'CAP-001',
    primaryBarcode: null,
    minSellingPrice: 8.0,
    maxSellingPrice: 8.0,
    createdAt: '2026-11-25T10:00:00Z',
    updatedAt: '2026-11-25T10:00:00Z',
    version: 1,
  },
  {
    id: 'prod-3',
    name: 'Draft Mug',
    brand: 'HomeCo',
    productType: 'SIMPLE',
    status: 'DRAFT',
    categoryId: null,
    categoryName: null,
    unitId: 'u-1',
    unitCode: 'PCS',
    unitSymbol: 'pcs',
    variantCount: 1,
    defaultSku: 'MUG-001',
    primaryBarcode: null,
    minSellingPrice: 5.0,
    maxSellingPrice: 5.0,
    createdAt: '2026-11-25T10:00:00Z',
    updatedAt: '2026-11-25T10:00:00Z',
    version: 0,
  },
];

describe('ProductsListPage', () => {
  const archiveMutateAsync = vi.fn();
  const restoreMutateAsync = vi.fn();
  const deleteDraftMutateAsync = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(permissionQueries.useCatalogReadBranchScope).mockReturnValue({
      canRead: true,
      isTenantScoped: true,
      authorizedBranchIds: [],
      resolvedBranchId: undefined,
      invalidBranchRequested: false,
    });
    vi.mocked(permissionQueries.useHasAnyScopePermission).mockReturnValue(true);
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);
    vi.mocked(categoryModule.useCategories).mockReturnValue({
      data: [
        {
          id: 'cat-1',
          parentId: null,
          code: 'APPAREL',
          name: 'Apparel',
          description: null,
          depth: 0,
          sortOrder: 0,
          isActive: true,
          createdAt: '',
          updatedAt: '',
          version: 0,
        },
      ],
    } as never);
    vi.mocked(unitModule.useUnits).mockReturnValue({
      data: [
        {
          id: 'u-1',
          code: 'PCS',
          name: 'Pieces',
          symbol: 'pcs',
          allowDecimal: false,
          decimalPrecision: 0,
          isSystem: true,
          isActive: true,
          createdAt: '',
          updatedAt: '',
          version: 0,
        },
      ],
    } as never);
    vi.mocked(branchModule.useBranches).mockReturnValue({ data: [] } as never);
    vi.mocked(productModule.useArchiveProduct).mockReturnValue({
      mutateAsync: archiveMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(productModule.useRestoreProduct).mockReturnValue({
      mutateAsync: restoreMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(productModule.useDeleteDraftProduct).mockReturnValue({
      mutateAsync: deleteDraftMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(productModule.useProducts).mockReturnValue({
      data: {
        items: SAMPLE_PRODUCTS,
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
        first: true,
        last: true,
      },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never);
  });

  it('renders products with SKU, unit code, price, and permission-gated actions', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <ProductsListPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.getByText('Plain Cotton Tee')).toBeInTheDocument();
    expect(screen.getByText('TEE-001')).toBeInTheDocument();
    expect(screen.getByText('BasicCo · PCS (pcs)')).toBeInTheDocument();
    expect(screen.getByText('12.50')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /New Product/i })).toBeInTheDocument();
    expect(screen.getByLabelText('Archive Plain Cotton Tee')).toBeInTheDocument();
    expect(screen.getByLabelText('Restore Archived Cap')).toBeInTheDocument();
  });

  it('hides write actions for read-only users', () => {
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(false);
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <ProductsListPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.queryByRole('button', { name: /New Product/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Edit Plain Cotton Tee')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Archive Plain Cotton Tee')).not.toBeInTheDocument();
    expect(screen.getByLabelText('View Plain Cotton Tee')).toBeInTheDocument();
  });

  it('fails closed when branch-only user requests an unauthorized branchId in URL', () => {
    vi.mocked(permissionQueries.useCatalogReadBranchScope).mockReturnValue({
      canRead: false,
      isTenantScoped: false,
      authorizedBranchIds: ['branch-1'],
      resolvedBranchId: undefined,
      invalidBranchRequested: true,
    });

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={['/catalog/products?branchId=branch-evil']}>
          <ProductsListPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(screen.getByText('Unauthorized Branch Context')).toBeInTheDocument();
    expect(screen.queryByText('Plain Cotton Tee')).not.toBeInTheDocument();
  });

  it('performs quick barcode/SKU lookup, handles lookup error, and archives product after confirmation', async () => {
    const lookupSpy = vi
      .spyOn(productModule.productApi, 'lookupVariant')
      .mockRejectedValueOnce(new Error('No active product variant found for code.'))
      .mockResolvedValueOnce({
        variantId: 'v-1',
        productId: 'prod-1',
        productName: 'Plain Cotton Tee',
        variantName: 'Default',
        sku: 'TEE-001',
        primaryBarcode: '4601000000011',
        optionValues: {},
        unitId: 'u-1',
        unitCode: 'PCS',
        unitSymbol: 'pcs',
        allowDecimal: false,
        decimalPrecision: 0,
        referenceCostPrice: 5,
        sellingPrice: 12.5,
        productStatus: 'ACTIVE',
        variantActive: true,
        activeDefault: true,
        sellable: true,
      });

    archiveMutateAsync.mockResolvedValueOnce({});

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <ProductsListPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    fireEvent.change(screen.getByLabelText('Lookup barcode or SKU'), {
      target: { value: 'UNKNOWN-SKU' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lookup' }));

    await waitFor(() => {
      expect(screen.getByText('No active product variant found for code.')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText('Lookup barcode or SKU'), {
      target: { value: '4601000000011' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Lookup' }));

    await waitFor(() => {
      expect(lookupSpy).toHaveBeenCalledWith('4601000000011', undefined);
      expect(screen.getByText('Sellable')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByLabelText('Archive Plain Cotton Tee'));
    fireEvent.click(screen.getByRole('button', { name: 'Archive Product' }));

    await waitFor(() => {
      expect(archiveMutateAsync).toHaveBeenCalledWith('prod-1');
    });
  });

  it('filters by unit and brand and deletes a draft product after confirmation', async () => {
    deleteDraftMutateAsync.mockResolvedValueOnce(undefined);

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <ProductsListPage />
        </MemoryRouter>
      </QueryClientProvider>
    );

    fireEvent.change(screen.getByLabelText('Filter by unit of measure'), {
      target: { value: 'u-1' },
    });
    fireEvent.change(screen.getByLabelText('Filter by brand'), {
      target: { value: 'BasicCo' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(productModule.useProducts).toHaveBeenLastCalledWith(
        expect.objectContaining({
          unitId: 'u-1',
          brand: 'BasicCo',
        })
      );
    });

    fireEvent.click(screen.getByLabelText('Delete draft Draft Mug'));
    fireEvent.click(screen.getByRole('button', { name: 'Delete Draft' }));

    await waitFor(() => {
      expect(deleteDraftMutateAsync).toHaveBeenCalledWith('prod-3');
    });
  });
});
