import React, { useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  useArchiveProduct,
  useDeleteDraftProduct,
  useProduct,
  useRestoreProduct,
  useUpdateProductStatus,
} from '@/entities/product';
import { PermissionGuard, useCatalogReadBranchScope } from '@/entities/permission';
import { VariantEditorSection } from '@/features/catalog/variant-editor/VariantEditorSection';
import { parseApiError } from '@/shared/api/errorHandler';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
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
  Archive,
  ArrowLeft,
  CheckCircle2,
  Edit3,
  Loader2,
  Package,
  RefreshCw,
  RotateCcw,
  Ruler,
  Tag,
  Trash2,
} from 'lucide-react';

export const ProductDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const branchIdParam = searchParams.get('branchId') ?? undefined;
  const productScope = useCatalogReadBranchScope('catalog.products.read', branchIdParam);

  const {
    data: product,
    isLoading,
    isError,
    error,
    refetch,
  } = useProduct(id ?? '', {
    branchId: branchIdParam,
    enabled: Boolean(id) && productScope.canRead,
  });

  const statusMutation = useUpdateProductStatus();
  const archiveMutation = useArchiveProduct();
  const restoreMutation = useRestoreProduct();
  const deleteDraftMutation = useDeleteDraftProduct();

  const isLifecyclePending =
    statusMutation.isPending ||
    archiveMutation.isPending ||
    restoreMutation.isPending ||
    deleteDraftMutation.isPending;

  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmAction, setConfirmAction] = useState<'archive' | 'deleteDraft' | null>(null);

  const catalogListUrl =
    branchIdParam && productScope.resolvedBranchId
      ? `/catalog/products?branchId=${encodeURIComponent(productScope.resolvedBranchId)}`
      : '/catalog/products';

  if (productScope.invalidBranchRequested) {
    return (
      <Card className="mx-auto mt-8 max-w-lg border-amber-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base text-slate-900">
            <AlertCircle className="h-5 w-5 text-amber-600" />
            <span>Unauthorized Branch Context</span>
          </CardTitle>
          <CardDescription>
            You do not have permission to view this product for the requested branch.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              const next = new URLSearchParams(searchParams);
              next.delete('branchId');
              setSearchParams(next);
            }}
          >
            Reset to Authorized Branch
          </Button>
          <Button variant="ghost" size="sm" onClick={() => navigate('/catalog/products')}>
            Back to Catalog
          </Button>
        </CardContent>
      </Card>
    );
  }

  const handlePublish = async () => {
    if (!product || isLifecyclePending) return;
    setActionError(null);
    try {
      await statusMutation.mutateAsync({ productId: product.id, status: 'ACTIVE' });
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to publish product.').message);
    }
  };

  const handleRestore = async () => {
    if (!product || isLifecyclePending) return;
    setActionError(null);
    try {
      await restoreMutation.mutateAsync(product.id);
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to restore product.').message);
    }
  };

  const handleConfirmedAction = async () => {
    if (!product || !confirmAction || isLifecyclePending) return;
    setActionError(null);
    try {
      if (confirmAction === 'archive') {
        await archiveMutation.mutateAsync(product.id);
        setConfirmAction(null);
      } else {
        await deleteDraftMutation.mutateAsync(product.id);
        setConfirmAction(null);
        navigate(catalogListUrl);
      }
    } catch (err) {
      setActionError(
        parseApiError(
          err,
          confirmAction === 'archive' ? 'Failed to archive product.' : 'Failed to delete draft product.'
        ).message
      );
      setConfirmAction(null);
    }
  };

  if (isLoading) {
    return (
      <div
        role="status"
        className="flex h-48 items-center justify-center gap-2 text-sm text-slate-500"
      >
        <Loader2 className="h-5 w-5 animate-spin" />
        <span>Loading product details...</span>
      </div>
    );
  }

  if (isError || !product) {
    return (
      <div className="space-y-4 max-w-xl mx-auto mt-8">
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700 space-y-3">
          <div>{parseApiError(error, 'Product could not be found.').message}</div>
          <div className="flex items-center justify-center gap-2">
            <Button variant="outline" size="sm" onClick={() => void refetch()} className="gap-1.5">
              <RefreshCw className="h-3.5 w-3.5" />
              <span>Retry</span>
            </Button>
            <Button variant="ghost" size="sm" onClick={() => navigate(catalogListUrl)}>
              Back to Catalog
            </Button>
          </div>
        </div>
      </div>
    );
  }

  const activeVariantsCount = product.variants.filter((v) => v.isActive).length;
  const activeDefaultVariant = product.variants.find((v) => v.isActiveDefault);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-4">
        <div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(catalogListUrl)}
            className="-ml-2 mb-1 gap-1.5 text-xs text-slate-600"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>Back to Catalog</span>
          </Button>

          <div className="flex items-center gap-2.5 flex-wrap">
            <Package className="h-6 w-6 text-slate-900" />
            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-slate-900 break-words">{product.name}</h1>
            <Badge
              variant={
                product.status === 'ACTIVE'
                  ? 'success'
                  : product.status === 'DRAFT'
                    ? 'warning'
                    : 'secondary'
              }
            >
              {product.status}
            </Badge>
            <Badge variant="outline">{product.productType}</Badge>
          </div>

          <p className="text-xs text-slate-500 mt-1">
            {product.brand ? `Brand: ${product.brand} · ` : ''}
            Updated {new Date(product.updatedAt).toLocaleString()}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <PermissionGuard permission="catalog.products.update">
            <Button
              variant="outline"
              size="sm"
              disabled={isLifecyclePending}
              onClick={() => navigate(`/catalog/products/${product.id}/edit`)}
              className="gap-1.5 text-xs"
            >
              <Edit3 className="h-3.5 w-3.5" />
              <span>Edit Metadata</span>
            </Button>

            {product.status === 'DRAFT' && (
              <Button
                size="sm"
                disabled={isLifecyclePending}
                onClick={() => void handlePublish()}
                className="gap-1.5 text-xs bg-emerald-600 hover:bg-emerald-700"
              >
                <CheckCircle2 className="h-3.5 w-3.5" />
                <span>Publish Product</span>
              </Button>
            )}

            {product.status === 'ARCHIVED' && (
              <Button
                size="sm"
                disabled={isLifecyclePending}
                onClick={() => void handleRestore()}
                className="gap-1.5 text-xs bg-emerald-600 hover:bg-emerald-700"
              >
                <RotateCcw className="h-3.5 w-3.5" />
                <span>Restore Product</span>
              </Button>
            )}
          </PermissionGuard>

          <PermissionGuard permission="catalog.products.delete">
            {product.status !== 'ARCHIVED' && (
              <Button
                variant="outline"
                size="sm"
                disabled={isLifecyclePending}
                onClick={() => setConfirmAction('archive')}
                className="gap-1.5 text-xs text-amber-700 border-amber-200 hover:bg-amber-50"
              >
                <Archive className="h-3.5 w-3.5" />
                <span>Archive</span>
              </Button>
            )}

            {product.status === 'DRAFT' && (
              <Button
                variant="destructive"
                size="sm"
                disabled={isLifecyclePending}
                onClick={() => setConfirmAction('deleteDraft')}
                className="gap-1.5 text-xs"
              >
                <Trash2 className="h-3.5 w-3.5" />
                <span>Delete Draft</span>
              </Button>
            )}
          </PermissionGuard>
        </div>
      </div>

      {actionError && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700"
        >
          <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
          <span>{actionError}</span>
        </div>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-xs font-medium text-slate-500 uppercase tracking-wider">
              <Tag className="h-3.5 w-3.5" />
              <span>Classification</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <div className="font-semibold text-slate-900">
              {product.category ? (
                <span>
                  {product.category.name}{' '}
                  <span className="font-mono text-xs text-slate-500">({product.category.code})</span>
                </span>
              ) : (
                <span className="text-slate-400">Uncategorized</span>
              )}
            </div>
            <div className="text-xs text-slate-500">
              Brand: {product.brand || 'Not specified'}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-xs font-medium text-slate-500 uppercase tracking-wider">
              <Ruler className="h-3.5 w-3.5" />
              <span>Unit of Measure</span>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <div className="font-semibold text-slate-900">
              {product.unit.name}{' '}
              <span className="font-mono text-xs text-slate-500">
                ({product.unit.code} / {product.unit.symbol})
              </span>
            </div>
            <div className="text-xs text-slate-500">
              {product.unit.allowDecimal
                ? `Decimal quantities up to ${product.unit.decimalPrecision} places`
                : 'Whole-number quantities only'}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-xs font-medium text-slate-500 uppercase tracking-wider">
              Variant Summary
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <div className="font-semibold text-slate-900">
              {activeVariantsCount} active of {product.variants.length} total
            </div>
            <div className="text-xs text-slate-500">
              Default SKU:{' '}
              <span className="font-mono font-medium text-slate-800">
                {activeDefaultVariant?.sku ?? 'None active'}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      {product.description && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm">Description</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-slate-700 whitespace-pre-line">{product.description}</p>
          </CardContent>
        </Card>
      )}

      {/* Variants & Barcodes Editor */}
      <VariantEditorSection product={product} />

      {/* Confirmation Dialog for Archive / Draft Deletion */}
      <Dialog open={!!confirmAction} onOpenChange={(o) => !o && setConfirmAction(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {confirmAction === 'archive' ? 'Archive Product' : 'Delete Draft Product'}
            </DialogTitle>
            <DialogDescription>
              {confirmAction === 'archive'
                ? `Archive "${product.name}"? All variants will be deactivated until restored.`
                : `Permanently delete draft product "${product.name}"? This cannot be undone.`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmAction(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={archiveMutation.isPending || deleteDraftMutation.isPending}
              onClick={() => void handleConfirmedAction()}
            >
              {(archiveMutation.isPending || deleteDraftMutation.isPending) && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              {confirmAction === 'archive' ? 'Archive Product' : 'Delete Draft'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};
