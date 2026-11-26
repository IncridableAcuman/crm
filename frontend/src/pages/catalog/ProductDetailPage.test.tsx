import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProductDetailPage } from './ProductDetailPage';
import * as productModule from '@/entities/product';
import * as permissionQueries from '@/entities/permission/model/permissionQueries';

vi.mock('@/entities/permission/model/permissionQueries', async () => {
  const actual = await vi.importActual<typeof import('@/entities/permission/model/permissionQueries')>(
    '@/entities/permission/model/permissionQueries'
  );
  return {
    ...actual,
    useHasPermission: vi.fn(),
    useCatalogReadBranchScope: vi.fn(),
  };
});

vi.mock('@/entities/product', async () => {
  const actual = await vi.importActual<typeof import('@/entities/product')>('@/entities/product');
  return {
    ...actual,
    useProduct: vi.fn(),
    useUpdateProductStatus: vi.fn(),
    useArchiveProduct: vi.fn(),
    useRestoreProduct: vi.fn(),
    useDeleteDraftProduct: vi.fn(),
    useAddVariant: vi.fn(),
    useUpdateVariant: vi.fn(),
    useChangeDefaultVariant: vi.fn(),
    useUpdateVariantStatus: vi.fn(),
    useConvertProductToVariable: vi.fn(),
    useAddBarcode: vi.fn(),
    useUpdateBarcode: vi.fn(),
    useSetPrimaryBarcode: vi.fn(),
    useRemoveBarcode: vi.fn(),
  };
});

const DRAFT_PRODUCT: productModule.ProductDetail = {
  id: 'prod-draft',
  name: 'Draft Espresso',
  description: 'Single origin beans',
  brand: 'RoastCo',
  productType: 'SIMPLE',
  variantOptions: [],
  status: 'DRAFT',
  category: { id: 'cat-1', code: 'COFFEE', name: 'Coffee' },
  unit: {
    id: 'u-kg',
    code: 'KG',
    name: 'Kilogram',
    symbol: 'kg',
    allowDecimal: true,
    decimalPrecision: 3,
  },
  variants: [
    {
      id: 'var-1',
      productId: 'prod-draft',
      sku: 'ESP-001',
      name: 'Default',
      optionValues: {},
      costPrice: 10,
      sellingPrice: 18.5,
      isDefault: true,
      isActiveDefault: true,
      isActive: true,
      barcodes: [
        {
          id: 'bc-1',
          variantId: 'var-1',
          barcode: '4609990001112',
          barcodeType: 'EAN13',
          isPrimary: true,
          createdAt: '2026-11-26T00:00:00Z',
          updatedAt: '2026-11-26T00:00:00Z',
        },
      ],
      createdAt: '2026-11-26T00:00:00Z',
      updatedAt: '2026-11-26T00:00:00Z',
      version: 0,
    },
  ],
  activeDefaultVariantId: 'var-1',
  historicalDefaultVariantId: 'var-1',
  createdAt: '2026-11-26T00:00:00Z',
  updatedAt: '2026-11-26T00:00:00Z',
  version: 0,
};

describe('ProductDetailPage', () => {
  const statusMutateAsync = vi.fn();
  const deleteMutateAsync = vi.fn();
  const convertMutateAsync = vi.fn();
  const removeBarcodeMutateAsync = vi.fn();
  const restoreMutateAsync = vi.fn();
  const noopMutation = { mutateAsync: vi.fn(), isPending: false } as never;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(permissionQueries.useHasPermission).mockReturnValue(true);
    vi.mocked(permissionQueries.useCatalogReadBranchScope).mockReturnValue({
      canRead: true,
      isTenantScoped: true,
      authorizedBranchIds: [],
      resolvedBranchId: undefined,
      invalidBranchRequested: false,
    });
    vi.mocked(productModule.useUpdateProductStatus).mockReturnValue({
      mutateAsync: statusMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(productModule.useDeleteDraftProduct).mockReturnValue({
      mutateAsync: deleteMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(productModule.useConvertProductToVariable).mockReturnValue({
      mutateAsync: convertMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(productModule.useRemoveBarcode).mockReturnValue({
      mutateAsync: removeBarcodeMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(productModule.useRestoreProduct).mockReturnValue({
      mutateAsync: restoreMutateAsync,
      isPending: false,
    } as never);
    vi.mocked(productModule.useArchiveProduct).mockReturnValue(noopMutation);
    vi.mocked(productModule.useAddVariant).mockReturnValue(noopMutation);
    vi.mocked(productModule.useUpdateVariant).mockReturnValue(noopMutation);
    vi.mocked(productModule.useChangeDefaultVariant).mockReturnValue(noopMutation);
    vi.mocked(productModule.useUpdateVariantStatus).mockReturnValue(noopMutation);
    vi.mocked(productModule.useAddBarcode).mockReturnValue(noopMutation);
    vi.mocked(productModule.useUpdateBarcode).mockReturnValue(noopMutation);
    vi.mocked(productModule.useSetPrimaryBarcode).mockReturnValue(noopMutation);
    vi.mocked(productModule.useProduct).mockReturnValue({
      data: DRAFT_PRODUCT,
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never);
  });

  const renderDetail = (initialPath = '/catalog/products/prod-draft') => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/catalog/products/:id" element={<ProductDetailPage />} />
            <Route path="/catalog/products" element={<div>Catalog List</div>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  it('displays DRAFT lifecycle controls, unit decimal precision, and variant barcodes', () => {
    renderDetail();

    expect(screen.getByText('Draft Espresso')).toBeInTheDocument();
    expect(screen.getByText('Decimal quantities up to 3 places')).toBeInTheDocument();
    expect(screen.getByText('4609990001112')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Publish Product/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Delete Draft/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Convert to Variable/i })).toBeInTheDocument();
  });

  it('does not expose unsupported ACTIVE -> DRAFT transition and shows Restore on ARCHIVED', async () => {
    vi.mocked(productModule.useProduct).mockReturnValue({
      data: { ...DRAFT_PRODUCT, status: 'ACTIVE' },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never);

    renderDetail();
    expect(screen.queryByRole('button', { name: /Move to Draft/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Delete Draft/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Archive/i })).toBeInTheDocument();

    vi.mocked(productModule.useProduct).mockReturnValue({
      data: { ...DRAFT_PRODUCT, status: 'ARCHIVED' },
      isLoading: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    } as never);
    restoreMutateAsync.mockResolvedValueOnce({ ...DRAFT_PRODUCT, status: 'ACTIVE' });

    renderDetail();
    fireEvent.click(screen.getByRole('button', { name: /Restore Product/i }));
    await waitFor(() => {
      expect(restoreMutateAsync).toHaveBeenCalledWith('prod-draft');
    });
  });

  it('publishes draft, converts SIMPLE to VARIABLE, requires confirmation for barcode removal, and deletes draft', async () => {
    statusMutateAsync.mockResolvedValueOnce({ ...DRAFT_PRODUCT, status: 'ACTIVE' });
    convertMutateAsync.mockResolvedValueOnce({ ...DRAFT_PRODUCT, productType: 'VARIABLE' });
    removeBarcodeMutateAsync.mockResolvedValueOnce(undefined);
    deleteMutateAsync.mockResolvedValueOnce(undefined);

    renderDetail();

    fireEvent.click(screen.getByRole('button', { name: /Publish Product/i }));
    await waitFor(() => {
      expect(statusMutateAsync).toHaveBeenCalledWith({
        productId: 'prod-draft',
        status: 'ACTIVE',
      });
    });

    // Convert SIMPLE -> VARIABLE
    fireEvent.click(screen.getByRole('button', { name: /Convert to Variable/i }));
    fireEvent.click(screen.getByRole('button', { name: 'Convert Product' }));
    await waitFor(() => {
      expect(convertMutateAsync).toHaveBeenCalledWith({
        productId: 'prod-draft',
        data: {
          variantOptions: ['Size'],
          existingDefaultOptionValues: { Size: 'Standard' },
        },
      });
    });

    // Remove barcode requires confirmation dialog
    fireEvent.click(screen.getByLabelText('Remove barcode 4609990001112'));
    expect(removeBarcodeMutateAsync).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Remove Barcode' }));
    await waitFor(() => {
      expect(removeBarcodeMutateAsync).toHaveBeenCalledWith({
        productId: 'prod-draft',
        variantId: 'var-1',
        barcodeId: 'bc-1',
      });
    });

    // Delete draft requires confirmation
    fireEvent.click(screen.getByRole('button', { name: 'Delete Draft' }));
    const confirmButtons = screen.getAllByRole('button', { name: 'Delete Draft' });
    fireEvent.click(confirmButtons[confirmButtons.length - 1]!);

    await waitFor(() => {
      expect(deleteMutateAsync).toHaveBeenCalledWith('prod-draft');
      expect(screen.getByText('Catalog List')).toBeInTheDocument();
    });
  });
});
