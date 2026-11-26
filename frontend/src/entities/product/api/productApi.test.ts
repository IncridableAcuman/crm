import { describe, it, expect, vi, beforeEach } from 'vitest';
import { productApi } from './productApi';
import { categoryApi } from '@/entities/category/api/categoryApi';
import { unitApi } from '@/entities/unit/api/unitApi';
import { apiClient } from '@/shared/api/client';

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('Catalog API clients (M05.4 contract alignment)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('productApi.listProducts strips blank query params and preserves branchId & pagination', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        },
        timestamp: '2026-11-26T00:00:00Z',
      },
    });

    await productApi.listProducts({
      search: '  coffee  ',
      sku: '   ',
      categoryId: 'cat-1',
      includeSubcategories: true,
      branchId: 'br-1',
      page: 1,
      size: 20,
      sort: 'name,asc',
    });

    expect(apiClient.get).toHaveBeenCalledWith('/products', {
      params: {
        search: 'coffee',
        categoryId: 'cat-1',
        includeSubcategories: true,
        branchId: 'br-1',
        page: 1,
        size: 20,
        sort: 'name,asc',
      },
    });
  });

  it('productApi.lookupVariant trims code and forwards optional branchId', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        success: true,
        data: { variantId: 'v1', productId: 'p1', sku: 'TEE-001', sellable: true },
        timestamp: '2026-11-26T00:00:00Z',
      },
    });

    const result = await productApi.lookupVariant('  4601000000011  ', 'br-1');
    expect(apiClient.get).toHaveBeenCalledWith('/products/lookup', {
      params: { code: '4601000000011', branchId: 'br-1' },
    });
    expect(result.sku).toBe('TEE-001');
  });

  it('productApi executes lifecycle, variant, and barcode endpoints cleanly', async () => {
    vi.mocked(apiClient.post).mockResolvedValue({ data: { success: true, data: {} } });
    vi.mocked(apiClient.patch).mockResolvedValue({ data: { success: true, data: {} } });
    vi.mocked(apiClient.delete).mockResolvedValue({ data: { success: true, data: null } });

    await productApi.archiveProduct('p1');
    expect(apiClient.post).toHaveBeenCalledWith('/products/p1/archive');

    await productApi.restoreProduct('p1');
    expect(apiClient.post).toHaveBeenCalledWith('/products/p1/restore');

    await productApi.deleteDraftProduct('p1');
    expect(apiClient.delete).toHaveBeenCalledWith('/products/p1');

    await productApi.changeDefaultVariant('p1', 'v2');
    expect(apiClient.patch).toHaveBeenCalledWith('/products/p1/variants/v2/default');

    await productApi.setPrimaryBarcode('p1', 'v2', 'b3');
    expect(apiClient.patch).toHaveBeenCalledWith('/products/p1/variants/v2/barcodes/b3/primary');
  });

  it('categoryApi and unitApi target canonical M05.4 endpoints with branchId', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { success: true, data: [] } });
    vi.mocked(apiClient.patch).mockResolvedValue({ data: { success: true, data: {} } });

    await categoryApi.getCategoryTree({ includeInactive: true, branchId: 'br-9' });
    expect(apiClient.get).toHaveBeenCalledWith('/categories/tree', {
      params: { includeInactive: true, branchId: 'br-9' },
    });

    await categoryApi.moveCategory('c1', { newParentId: null });
    expect(apiClient.patch).toHaveBeenCalledWith('/categories/c1/move', { newParentId: null });

    await unitApi.listUnits({ includeInactive: false, branchId: 'br-9' });
    expect(apiClient.get).toHaveBeenCalledWith('/units', {
      params: { includeInactive: false, branchId: 'br-9' },
    });
  });
});
