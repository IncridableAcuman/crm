import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useCatalogReadBranchScope, permissionKeys } from './permissionQueries';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { useProducts } from '@/entities/product/model/productQueries';
import { useCategoryTree } from '@/entities/category/model/categoryQueries';
import { useUnits } from '@/entities/unit/model/unitQueries';
import { productApi } from '@/entities/product/api/productApi';
import { categoryApi } from '@/entities/category/api/categoryApi';
import { unitApi } from '@/entities/unit/api/unitApi';

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

describe('Catalog Branch Context & Tenant Isolation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.getState().setAccessToken('tenant-jwt');
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

  it('requires authorized branchId for branch-only user and fails closed on unauthorized branchId', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(permissionKeys.mine('tenant-A'), {
      tenantId: 'tenant-A',
      membershipId: 'mem-1',
      tenantPermissions: [],
      branchPermissions: {
        'branch-1': ['catalog.products.read'],
        'branch-2': ['catalog.products.read'],
      },
    });

    const { result: defaultResult } = renderHook(
      () => useCatalogReadBranchScope('catalog.products.read'),
      { wrapper: createWrapper(qc) }
    );
    expect(defaultResult.current.canRead).toBe(true);
    expect(defaultResult.current.isTenantScoped).toBe(false);
    expect(defaultResult.current.resolvedBranchId).toBe('branch-1');
    expect(defaultResult.current.invalidBranchRequested).toBe(false);

    const { result: secondBranchResult } = renderHook(
      () => useCatalogReadBranchScope('catalog.products.read', 'branch-2'),
      { wrapper: createWrapper(qc) }
    );
    expect(secondBranchResult.current.canRead).toBe(true);
    expect(secondBranchResult.current.resolvedBranchId).toBe('branch-2');
    expect(secondBranchResult.current.invalidBranchRequested).toBe(false);

    const { result: unauthorizedResult } = renderHook(
      () => useCatalogReadBranchScope('catalog.products.read', 'branch-evil'),
      { wrapper: createWrapper(qc) }
    );
    expect(unauthorizedResult.current.canRead).toBe(false);
    expect(unauthorizedResult.current.resolvedBranchId).toBeUndefined();
    expect(unauthorizedResult.current.invalidBranchRequested).toBe(true);
  });

  it('prevents catalog GET queries when branch-only user has unauthorized branchId and isolates cache across branch switches', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(permissionKeys.mine('tenant-A'), {
      tenantId: 'tenant-A',
      membershipId: 'mem-1',
      tenantPermissions: [],
      branchPermissions: {
        'branch-1': ['catalog.products.read', 'catalog.categories.read'],
        'branch-2': ['catalog.products.read', 'catalog.categories.read'],
      },
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

    renderHook(() => useProducts({ branchId: 'branch-unauthorized' }), {
      wrapper: createWrapper(qc),
    });
    expect(productApi.listProducts).not.toHaveBeenCalled();

    const { rerender } = renderHook(
      ({ branchId }: { branchId?: string }) => {
        const products = useProducts({ branchId });
        const tree = useCategoryTree({ branchId });
        const units = useUnits({ branchId });
        return { products, tree, units };
      },
      {
        wrapper: createWrapper(qc),
        initialProps: { branchId: undefined as string | undefined },
      }
    );

    await waitFor(() => {
      expect(productApi.listProducts).toHaveBeenCalledWith({ branchId: 'branch-1' });
      expect(categoryApi.getCategoryTree).toHaveBeenCalledWith({
        includeInactive: false,
        branchId: 'branch-1',
      });
      expect(unitApi.listUnits).toHaveBeenCalledWith({
        includeInactive: false,
        branchId: 'branch-1',
      });
    });

    rerender({ branchId: 'branch-2' });

    await waitFor(() => {
      expect(productApi.listProducts).toHaveBeenCalledWith({ branchId: 'branch-2' });
      expect(categoryApi.getCategoryTree).toHaveBeenCalledWith({
        includeInactive: false,
        branchId: 'branch-2',
      });
      expect(unitApi.listUnits).toHaveBeenCalledWith({
        includeInactive: false,
        branchId: 'branch-2',
      });
    });

    const fetchedProductKeys = qc
      .getQueryCache()
      .getAll()
      .filter((q) => q.queryKey[0] === 'catalog' && q.queryKey[2] === 'products' && q.state.dataUpdatedAt > 0)
      .map((q) => q.queryKey);
    expect(fetchedProductKeys).toHaveLength(2);
    expect(fetchedProductKeys[0]?.[1]).toBe('tenant-A');
    expect((fetchedProductKeys[0]?.[4] as { branchId?: string })?.branchId).toBe('branch-1');
    expect((fetchedProductKeys[1]?.[4] as { branchId?: string })?.branchId).toBe('branch-2');
  });

  it('allows tenant-scoped users to omit branchId and isolates query keys by tenantId', async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(permissionKeys.mine('tenant-A'), {
      tenantId: 'tenant-A',
      membershipId: 'mem-1',
      tenantPermissions: ['catalog.products.read'],
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

    renderHook(() => useProducts({}), { wrapper: createWrapper(qc) });

    await waitFor(() => {
      expect(productApi.listProducts).toHaveBeenCalledWith({ branchId: undefined });
    });

    // Simulate tenant switch + queryClient.clear()
    act(() => {
      qc.clear();
      useAuthStore.getState().setActiveTenant({
        tenantId: 'tenant-B',
        tenantName: 'Store B',
        tenantCode: 'STORE_B',
        membershipStatus: 'ACTIVE',
        current: true,
      });
      qc.setQueryData(permissionKeys.mine('tenant-B'), {
        tenantId: 'tenant-B',
        membershipId: 'mem-2',
        tenantPermissions: ['catalog.products.read'],
        branchPermissions: {},
      });
    });

    renderHook(() => useProducts({}), { wrapper: createWrapper(qc) });

    await waitFor(() => {
      const keys = qc
        .getQueryCache()
        .getAll()
        .filter((q) => q.queryKey[0] === 'catalog')
        .map((q) => q.queryKey);
      expect(keys.every((k) => k[1] === 'tenant-B')).toBe(true);
    });
  });
});
