import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  ProductStatus,
  ProductSummary,
  ProductType,
  VariantLookup,
  productApi,
  useArchiveProduct,
  useDeleteDraftProduct,
  useProducts,
  useRestoreProduct,
} from '@/entities/product';
import { useCategories } from '@/entities/category';
import { useUnits } from '@/entities/unit';
import { useBranches } from '@/entities/branch';
import {
  PermissionGuard,
  useCatalogReadBranchScope,
  useHasAnyScopePermission,
} from '@/entities/permission';
import { ProductTable } from '@/widgets/product-table/ProductTable';
import { parseApiError } from '@/shared/api/errorHandler';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Input } from '@/shared/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog';
import {
  AlertCircle,
  Barcode,
  Loader2,
  Package,
  Plus,
  RefreshCw,
  Search,
  X,
} from 'lucide-react';

const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest First' },
  { value: 'updatedAt,desc', label: 'Recently Updated' },
  { value: 'name,asc', label: 'Name (A → Z)' },
  { value: 'name,desc', label: 'Name (Z → A)' },
  { value: 'status,asc', label: 'Status' },
] as const;

export const ProductsListPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const searchParam = searchParams.get('search') ?? '';
  const categoryIdParam = searchParams.get('categoryId') ?? '';
  const unitIdParam = searchParams.get('unitId') ?? '';
  const brandParam = searchParams.get('brand') ?? '';
  const includeSubcategoriesParam = searchParams.get('includeSubcategories') === 'true';
  const statusFilterParam = searchParams.get('status') ?? 'DEFAULT';
  const productTypeParam = (searchParams.get('productType') ?? '') as ProductType | '';
  const sortParam = searchParams.get('sort') ?? 'createdAt,desc';
  const pageParam = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0);
  const branchIdParam = searchParams.get('branchId') ?? undefined;

  const [searchInput, setSearchInput] = useState(searchParam);
  const [brandInput, setBrandInput] = useState(brandParam);
  const [lookupCode, setLookupCode] = useState('');
  const [lookupResult, setLookupResult] = useState<VariantLookup | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [lookupPending, setLookupPending] = useState(false);

  const [archiveTarget, setArchiveTarget] = useState<ProductSummary | null>(null);
  const [deleteDraftTarget, setDeleteDraftTarget] = useState<ProductSummary | null>(null);
  const [pendingProductId, setPendingProductId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const productScope = useCatalogReadBranchScope('catalog.products.read', branchIdParam);
  const canReadCategories = useHasAnyScopePermission('catalog.categories.read');
  const canReadBranches = useHasAnyScopePermission('tenancy.branches.read');

  useEffect(() => {
    setLookupResult(null);
    setLookupError(null);
  }, [productScope.resolvedBranchId]);

  const { data: branches = [] } = useBranches({ enabled: canReadBranches });
  const { data: categories = [] } = useCategories({
    includeInactive: false,
    branchId: branchIdParam,
    enabled: canReadCategories,
  });
  const { data: units = [] } = useUnits({
    includeInactive: false,
    branchId: branchIdParam,
    enabled: productScope.canRead,
  });

  const queryParams = useMemo(() => {
    let status: ProductStatus | undefined;
    let includeArchived = false;
    if (statusFilterParam === 'ACTIVE' || statusFilterParam === 'DRAFT' || statusFilterParam === 'ARCHIVED') {
      status = statusFilterParam;
    } else if (statusFilterParam === 'ALL_WITH_ARCHIVED') {
      includeArchived = true;
    }

    return {
      search: searchParam || undefined,
      categoryId: categoryIdParam || undefined,
      includeSubcategories: categoryIdParam ? includeSubcategoriesParam : undefined,
      unitId: unitIdParam || undefined,
      brand: brandParam || undefined,
      status,
      productType: productTypeParam || undefined,
      includeArchived,
      branchId: productScope.resolvedBranchId,
      page: pageParam,
      size: 20,
      sort: sortParam,
    };
  }, [
    searchParam,
    categoryIdParam,
    unitIdParam,
    brandParam,
    includeSubcategoriesParam,
    statusFilterParam,
    productTypeParam,
    productScope.resolvedBranchId,
    pageParam,
    sortParam,
  ]);

  const { data: pageData, isLoading, isError, error, refetch } = useProducts(queryParams);
  const archiveMutation = useArchiveProduct();
  const restoreMutation = useRestoreProduct();
  const deleteDraftMutation = useDeleteDraftProduct();

  const updateParams = (updates: Record<string, string | null>, resetPage = true) => {
    const next = new URLSearchParams(searchParams);
    for (const [key, val] of Object.entries(updates)) {
      if (val === null || val === '') {
        next.delete(key);
      } else {
        next.set(key, val);
      }
    }
    if (resetPage && !('page' in updates)) {
      next.delete('page');
    }
    setSearchParams(next);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateParams({
      search: searchInput.trim() || null,
      brand: brandInput.trim() || null,
    });
  };

  const handleClearFilters = () => {
    setSearchInput('');
    setBrandInput('');
    setSearchParams(branchIdParam ? { branchId: branchIdParam } : {});
  };

  const handleLookupSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!productScope.canRead) return;
    const trimmed = lookupCode.trim();
    if (!trimmed) return;
    setLookupPending(true);
    setLookupError(null);
    setLookupResult(null);
    try {
      const result = await productApi.lookupVariant(trimmed, productScope.resolvedBranchId);
      setLookupResult(result);
    } catch (err) {
      setLookupError(parseApiError(err, 'No sellable variant matched that barcode or SKU.').message);
    } finally {
      setLookupPending(false);
    }
  };

  const handleConfirmArchive = async () => {
    if (!archiveTarget) return;
    setActionError(null);
    setPendingProductId(archiveTarget.id);
    try {
      await archiveMutation.mutateAsync(archiveTarget.id);
      setArchiveTarget(null);
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to archive product.').message);
      setArchiveTarget(null);
    } finally {
      setPendingProductId(null);
    }
  };

  const handleRestore = async (product: ProductSummary) => {
    setActionError(null);
    setPendingProductId(product.id);
    try {
      await restoreMutation.mutateAsync(product.id);
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to restore product.').message);
    } finally {
      setPendingProductId(null);
    }
  };

  const handleConfirmDeleteDraft = async () => {
    if (!deleteDraftTarget) return;
    setActionError(null);
    setPendingProductId(deleteDraftTarget.id);
    try {
      await deleteDraftMutation.mutateAsync(deleteDraftTarget.id);
      setDeleteDraftTarget(null);
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to delete draft product.').message);
      setDeleteDraftTarget(null);
    } finally {
      setPendingProductId(null);
    }
  };

  const buildProductDetailUrl = (productId: string) =>
    branchIdParam && productScope.resolvedBranchId
      ? `/catalog/products/${productId}?branchId=${encodeURIComponent(productScope.resolvedBranchId)}`
      : `/catalog/products/${productId}`;

  if (productScope.invalidBranchRequested) {
    return (
      <Card className="mx-auto mt-8 max-w-lg border-amber-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base text-slate-900">
            <AlertCircle className="h-5 w-5 text-amber-600" />
            <span>Unauthorized Branch Context</span>
          </CardTitle>
          <CardDescription>
            You do not have permission to view the product catalog for the requested branch.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="outline"
            size="sm"
            onClick={() => updateParams({ branchId: null })}
          >
            Reset to Authorized Branch
          </Button>
        </CardContent>
      </Card>
    );
  }

  const hasActiveFilters =
    Boolean(searchParam) ||
    Boolean(categoryIdParam) ||
    Boolean(unitIdParam) ||
    Boolean(brandParam) ||
    statusFilterParam !== 'DEFAULT' ||
    Boolean(productTypeParam);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Package className="h-6 w-6 text-slate-900" />
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900">Product Catalog</h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Search and manage products, variant matrices, SKUs, barcodes, and reference pricing.
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {!productScope.isTenantScoped && productScope.authorizedBranchIds.length > 1 && (
            <select
              aria-label="Branch read context"
              value={productScope.resolvedBranchId ?? ''}
              onChange={(e) => updateParams({ branchId: e.target.value || null })}
              className="h-9 rounded-md border border-slate-200 bg-white px-3 text-xs shadow-sm"
            >
              {productScope.authorizedBranchIds.map((bId) => {
                const branch = branches.find((b) => b.id === bId);
                return (
                  <option key={bId} value={bId}>
                    {branch ? `${branch.name} (${branch.code})` : bId}
                  </option>
                );
              })}
            </select>
          )}

          <PermissionGuard permission="catalog.products.create">
            <Button
              size="sm"
              onClick={() => navigate('/catalog/products/new')}
              className="gap-1.5 bg-slate-900 hover:bg-slate-800"
            >
              <Plus className="h-4 w-4" />
              <span>New Product</span>
            </Button>
          </PermissionGuard>
        </div>
      </div>

      {/* Quick Barcode / SKU Lookup */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-sm">
            <Barcode className="h-4 w-4 text-slate-700" />
            <span>Quick Barcode / SKU Lookup</span>
          </CardTitle>
          <CardDescription className="text-xs">
            Exact barcode or SKU check for active sellable variants (POS-ready lookup).
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <form onSubmit={handleLookupSubmit} className="flex flex-col sm:flex-row gap-2 max-w-xl">
            <Input
              aria-label="Lookup barcode or SKU"
              placeholder="Scan barcode or enter exact SKU (e.g. 4601000000011 or TEE-001)"
              value={lookupCode}
              onChange={(e) => setLookupCode(e.target.value)}
              className="font-mono text-xs"
            />
            <Button
              type="submit"
              variant="outline"
              size="sm"
              disabled={lookupPending || !lookupCode.trim()}
              className="gap-1.5 shrink-0"
            >
              {lookupPending ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <Search className="h-3.5 w-3.5" />
              )}
              <span>Lookup</span>
            </Button>
          </form>

          {lookupError && (
            <div role="alert" className="flex items-center justify-between rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
              <span>{lookupError}</span>
              <button type="button" onClick={() => setLookupError(null)} aria-label="Dismiss lookup error">
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          )}

          {lookupResult && (
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-lg border border-emerald-200 bg-emerald-50/50 p-3 text-xs">
              <div className="space-y-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-semibold text-slate-900">{lookupResult.productName}</span>
                  <span className="text-slate-600">— {lookupResult.variantName}</span>
                  <Badge variant="outline" className="font-mono text-[10px]">
                    {lookupResult.sku}
                  </Badge>
                  <Badge variant={lookupResult.sellable ? 'success' : 'secondary'} className="text-[10px]">
                    {lookupResult.sellable ? 'Sellable' : 'Not Sellable'}
                  </Badge>
                </div>
                <div className="text-slate-600">
                  Price: <span className="font-mono font-semibold">{Number(lookupResult.sellingPrice).toFixed(2)}</span>
                  {' · '}Unit: {lookupResult.unitCode} ({lookupResult.unitSymbol})
                  {lookupResult.primaryBarcode ? ` · Barcode: ${lookupResult.primaryBarcode}` : ''}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => navigate(buildProductDetailUrl(lookupResult.productId))}
                  className="h-7 text-xs"
                >
                  Open Product
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setLookupResult(null)}
                  aria-label="Clear lookup result"
                  className="h-7 px-2"
                >
                  <X className="h-3.5 w-3.5" />
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Catalog Filters & Table */}
      <Card>
        <CardHeader className="pb-4 space-y-4">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
            <form onSubmit={handleSearchSubmit} className="flex flex-wrap items-center gap-2 flex-1 max-w-2xl">
              <div className="relative flex-1 min-w-[200px]">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-slate-400" />
                <Input
                  aria-label="Search products"
                  placeholder="Search name, brand, SKU, or barcode..."
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  className="pl-8 text-sm"
                />
              </div>
              <Input
                aria-label="Filter by brand"
                placeholder="Brand..."
                value={brandInput}
                onChange={(e) => setBrandInput(e.target.value)}
                className="w-36 text-xs"
              />
              <Button type="submit" variant="outline" size="sm">
                Search
              </Button>
            </form>

            <div className="flex flex-wrap items-center gap-2">
              {canReadCategories && (
                <select
                  aria-label="Filter by category"
                  value={categoryIdParam}
                  onChange={(e) =>
                    updateParams({
                      categoryId: e.target.value || null,
                      includeSubcategories: e.target.value ? String(includeSubcategoriesParam) : null,
                    })
                  }
                  className="h-9 rounded-md border border-slate-200 bg-white px-3 text-xs shadow-sm"
                >
                  <option value="">All Categories</option>
                  {categories.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {'— '.repeat(cat.depth)}
                      {cat.name}
                    </option>
                  ))}
                </select>
              )}

              {categoryIdParam && (
                <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer px-1">
                  <input
                    type="checkbox"
                    checked={includeSubcategoriesParam}
                    onChange={(e) =>
                      updateParams({ includeSubcategories: e.target.checked ? 'true' : null })
                    }
                    className="rounded border-slate-300"
                  />
                  <span>Include subcategories</span>
                </label>
              )}

              <select
                aria-label="Filter by unit of measure"
                value={unitIdParam}
                onChange={(e) => updateParams({ unitId: e.target.value || null })}
                className="h-9 rounded-md border border-slate-200 bg-white px-3 text-xs shadow-sm"
              >
                <option value="">All Units</option>
                {units.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name} ({u.code})
                  </option>
                ))}
              </select>

              <select
                aria-label="Filter by product type"
                value={productTypeParam}
                onChange={(e) => updateParams({ productType: e.target.value || null })}
                className="h-9 rounded-md border border-slate-200 bg-white px-3 text-xs shadow-sm"
              >
                <option value="">All Types</option>
                <option value="SIMPLE">SIMPLE</option>
                <option value="VARIABLE">VARIABLE</option>
              </select>

              <select
                aria-label="Filter by status"
                value={statusFilterParam}
                onChange={(e) =>
                  updateParams({ status: e.target.value === 'DEFAULT' ? null : e.target.value })
                }
                className="h-9 rounded-md border border-slate-200 bg-white px-3 text-xs shadow-sm"
              >
                <option value="DEFAULT">Active & Draft</option>
                <option value="ACTIVE">Active Only</option>
                <option value="DRAFT">Draft Only</option>
                <option value="ARCHIVED">Archived Only</option>
                <option value="ALL_WITH_ARCHIVED">All (Incl. Archived)</option>
              </select>

              <select
                aria-label="Sort products"
                value={sortParam}
                onChange={(e) => updateParams({ sort: e.target.value })}
                className="h-9 rounded-md border border-slate-200 bg-white px-3 text-xs shadow-sm"
              >
                {SORT_OPTIONS.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>

              {hasActiveFilters && (
                <Button variant="ghost" size="sm" onClick={handleClearFilters} className="text-xs h-9">
                  Reset
                </Button>
              )}
            </div>
          </div>
        </CardHeader>

        <CardContent className="space-y-4">
          {actionError && (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700"
            >
              <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
              <span>{actionError}</span>
            </div>
          )}

          {isLoading ? (
            <div
              role="status"
              className="flex h-40 items-center justify-center gap-2 text-sm text-slate-500"
            >
              <Loader2 className="h-5 w-5 animate-spin" />
              <span>Loading products...</span>
            </div>
          ) : isError ? (
            <div
              role="alert"
              className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700 space-y-3"
            >
              <div>{parseApiError(error, 'Failed to load product catalog.').message}</div>
              <Button variant="outline" size="sm" onClick={() => void refetch()} className="gap-1.5">
                <RefreshCw className="h-3.5 w-3.5" />
                <span>Retry</span>
              </Button>
            </div>
          ) : !pageData || pageData.items.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center text-slate-500 space-y-3">
              <Package className="h-10 w-10 text-slate-300" />
              <div>
                <p className="font-medium text-slate-800">
                  {hasActiveFilters ? 'No products match your filters' : 'No products in catalog yet'}
                </p>
                <p className="text-xs text-slate-400 mt-1 max-w-md">
                  {hasActiveFilters
                    ? 'Try clearing your search query or adjusting status/category filters.'
                    : 'Create your first simple or variable product with SKUs, units, and barcodes.'}
                </p>
              </div>
              {hasActiveFilters ? (
                <Button variant="outline" size="sm" onClick={handleClearFilters}>
                  Clear Filters
                </Button>
              ) : (
                <PermissionGuard permission="catalog.products.create">
                  <Button
                    size="sm"
                    onClick={() => navigate('/catalog/products/new')}
                    className="gap-1.5 bg-slate-900 hover:bg-slate-800"
                  >
                    <Plus className="h-4 w-4" />
                    <span>Create Product</span>
                  </Button>
                </PermissionGuard>
              )}
            </div>
          ) : (
            <ProductTable
              pageData={pageData}
              pendingProductId={pendingProductId}
              onView={(productId) => navigate(buildProductDetailUrl(productId))}
              onEdit={(productId) => navigate(`/catalog/products/${productId}/edit`)}
              onArchive={(product) => setArchiveTarget(product)}
              onRestore={(product) => void handleRestore(product)}
              onDeleteDraft={(product) => setDeleteDraftTarget(product)}
              onPageChange={(nextPage) =>
                updateParams({ page: nextPage <= 0 ? null : String(nextPage) }, false)
              }
            />
          )}
        </CardContent>
      </Card>

      {/* Archive Product Confirmation Dialog */}
      <Dialog open={!!archiveTarget} onOpenChange={(o) => !o && setArchiveTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Archive Product</DialogTitle>
            <DialogDescription>
              Are you sure you want to archive <span className="font-semibold">{archiveTarget?.name}</span>?
              Archived products and their variants are hidden from active sales and can be restored later.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setArchiveTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={archiveMutation.isPending}
              onClick={() => void handleConfirmArchive()}
            >
              {archiveMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Archive Product
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
      {/* Delete Draft Product Confirmation Dialog */}
      <Dialog open={!!deleteDraftTarget} onOpenChange={(o) => !o && setDeleteDraftTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete Draft Product</DialogTitle>
            <DialogDescription>
              Permanently delete draft product{' '}
              <span className="font-semibold">{deleteDraftTarget?.name}</span>? This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDraftTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteDraftMutation.isPending}
              onClick={() => void handleConfirmDeleteDraft()}
            >
              {deleteDraftMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Delete Draft
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};
