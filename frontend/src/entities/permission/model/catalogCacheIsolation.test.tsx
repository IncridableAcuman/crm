import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { permissionKeys } from './permissionQueries';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { useProducts, productKeys } from '@/entities/product/model/productQueries';
import { useCategoryTree, categoryKeys } from '@/entities/category/model/categoryQueries';
import { useUnits, unitKeys } from '@/entities/unit/model/unitQueries';
import { productApi } from '@/entities/product/api/productApi';
import { categoryApi } from '@/entities/category/api/categoryApi';
import { unitApi } from '@/entities/unit/api/unitApi';
import { PageResponse } from '@/entities/session/model/types';
import { ProductSummary } from '@/entities/product/model/types';

vi.mock('@/entities/product/api/productApi', () => ({
  productApi: {
    listProducts: vi.fn(),
  },
}));

vi.mock('@/entities/category/api/categoryApi', () => ({
  categoryApi: {
    getCategoryTree: vi.fn(),
    listCategories: vi.fn(),
  },
}));

vi.mock('@/entities/unit/api/unitApi', () => ({
  unitApi: {
    listUnits: vi.fn(),
  },
}));

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('M05.6 Catalog Tenant & Branch Cache Isolation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.getState().clearAuth();
    useAuthStore.getState().setAccessToken('jwt-tenant-a');
    useAuthStore.getState().setActiveTenant({
      tenantId: 'tenant-A',
      tenantName: 'Store A',
      tenantCode: 'STORE_A',
      membershipStatus: 'ACTIVE',
      current: true,
    });
  });

  const createWrapper = (qc: QueryClient) => {
    return ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    );
  };

  it('prevents late in-flight Tenant A responses from repopulating Tenant B cache after tenant switch', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(permissionKeys.mine('tenant-A'), {
      tenantId: 'tenant-A',
      membershipId: 'mem-a',
      tenantPermissions: ['catalog.products.read', 'catalog.categories.read'],
      branchPermissions: {},
    });

    const tenantADeferred = createDeferred<PageResponse<ProductSummary>>();
    vi.mocked(productApi.listProducts)
      .mockReturnValueOnce(tenantADeferred.promise)
      .mockResolvedValueOnce({
        items: [
          {
            id: 'prod-b',
            name: 'Tenant B Only Product',
            brand: null,
            productType: 'SIMPLE',
            status: 'ACTIVE',
            categoryId: null,
            categoryName: null,
            unitId: 'u-b',
            unitCode: 'PCS',
            unitSymbol: 'pcs',
            variantCount: 1,
            defaultSku: 'B-001',
            primaryBarcode: null,
            minSellingPrice: 10,
            maxSellingPrice: 10,
            createdAt: '',
            updatedAt: '',
            version: 0,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      });

    const { rerender } = renderHook(() => useProducts({}), { wrapper: createWrapper(qc) });

    // Perform TenantSelectPage switch sequence while Tenant A request is still in flight
    await act(async () => {
      await qc.cancelQueries();
      qc.clear();
      useAuthStore.getState().setAccessToken('jwt-tenant-b');
      useAuthStore.getState().setActiveTenant({
        tenantId: 'tenant-B',
        tenantName: 'Store B',
        tenantCode: 'STORE_B',
        membershipStatus: 'ACTIVE',
        current: true,
      });
      qc.setQueryData(permissionKeys.mine('tenant-B'), {
        tenantId: 'tenant-B',
        membershipId: 'mem-b',
        tenantPermissions: ['catalog.products.read', 'catalog.categories.read'],
        branchPermissions: {},
      });
    });

    rerender();

    // Now resolve the stale Tenant A request AFTER Tenant B is active
    await act(async () => {
      tenantADeferred.resolve({
        items: [
          {
            id: 'prod-a-secret',
            name: 'Tenant A Confidential Product',
            brand: null,
            productType: 'SIMPLE',
            status: 'ACTIVE',
            categoryId: null,
            categoryName: null,
            unitId: 'u-a',
            unitCode: 'PCS',
            unitSymbol: 'pcs',
            variantCount: 1,
            defaultSku: 'A-SECRET',
            primaryBarcode: null,
            minSellingPrice: 99,
            maxSellingPrice: 99,
            createdAt: '',
            updatedAt: '',
            version: 0,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      });
    });

    await waitFor(() => {
      const bData = qc.getQueryData<PageResponse<ProductSummary>>(productKeys.list('tenant-B', {}));
      expect(bData?.items[0]?.name).toBe('Tenant B Only Product');
    });

    // Tenant A data must NOT exist anywhere in Tenant B's active cache
    const allQueries = qc.getQueryCache().getAll();
    expect(allQueries.some((q) => JSON.stringify(q.state.data ?? '').includes('Tenant A Confidential Product'))).toBe(false);
    expect(allQueries.filter((q) => q.queryKey[0] === 'catalog').every((q) => q.queryKey[1] === 'tenant-B')).toBe(true);
  });

  it('purges all catalog product, category, and unit caches on logout', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(permissionKeys.mine('tenant-A'), {
      tenantId: 'tenant-A',
      membershipId: 'mem-a',
      tenantPermissions: ['catalog.products.read', 'catalog.categories.read'],
      branchPermissions: {},
    });
    vi.mocked(productApi.listProducts).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
    vi.mocked(categoryApi.getCategoryTree).mockResolvedValue([]);
    vi.mocked(unitApi.listUnits).mockResolvedValue([]);

    const { unmount } = renderHook(
      () => ({
        products: useProducts({}),
        tree: useCategoryTree({}),
        units: useUnits({}),
      }),
      { wrapper: createWrapper(qc) }
    );

    await waitFor(() => {
      expect(qc.getQueryData(productKeys.list('tenant-A', {}))).toBeDefined();
      expect(qc.getQueryData(categoryKeys.tree('tenant-A', false))).toBeDefined();
      expect(qc.getQueryData(unitKeys.list('tenant-A', false))).toBeDefined();
    });

    // Simulate authApi.logout() cleanup + navigation unmount to /login
    await act(async () => {
      await qc.cancelQueries();
      useAuthStore.getState().clearAuth();
      qc.clear();
      unmount();
    });

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(qc.getQueryData(productKeys.list('tenant-A', {}))).toBeUndefined();
    expect(qc.getQueryData(categoryKeys.tree('tenant-A', false))).toBeUndefined();
    expect(qc.getQueryData(unitKeys.list('tenant-A', false))).toBeUndefined();
    expect(qc.getQueryCache().getAll().every((q) => q.state.data === undefined)).toBe(true);
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });
});
