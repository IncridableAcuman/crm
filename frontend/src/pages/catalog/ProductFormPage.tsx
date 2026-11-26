import React from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  CreateProductRequest,
  UpdateProductRequest,
  useCreateProduct,
  useProduct,
  useUpdateProduct,
} from '@/entities/product';
import { useCategories } from '@/entities/category';
import { useUnits } from '@/entities/unit';
import { useHasAnyScopePermission } from '@/entities/permission';
import { ProductForm } from '@/features/catalog/product-form/ProductForm';
import { parseApiError } from '@/shared/api/errorHandler';
import { Button } from '@/shared/ui/button';
import { ArrowLeft, Loader2, Package, RefreshCw } from 'lucide-react';

export interface ProductFormPageProps {
  mode: 'create' | 'edit';
}

export const ProductFormPage: React.FC<ProductFormPageProps> = ({ mode }) => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();

  const canReadCategories = useHasAnyScopePermission('catalog.categories.read');
  const {
    data: categories = [],
    isLoading: categoriesLoading,
  } = useCategories({ includeInactive: false, enabled: canReadCategories });
  const {
    data: units = [],
    isLoading: unitsLoading,
    isError: unitsError,
    error: unitsErrorObj,
    refetch: refetchUnits,
  } = useUnits({ includeInactive: false });

  const {
    data: product,
    isLoading: productLoading,
    isError: productError,
    error: productErrorObj,
    refetch: refetchProduct,
  } = useProduct(id ?? '', { enabled: mode === 'edit' && Boolean(id) });

  const createMutation = useCreateProduct();
  const updateMutation = useUpdateProduct();

  const backTarget = mode === 'edit' && id ? `/catalog/products/${id}` : '/catalog/products';

  const handleCreateSubmit = async (payload: CreateProductRequest) => {
    const created = await createMutation.mutateAsync(payload);
    navigate(`/catalog/products/${created.id}`);
  };

  const handleEditSubmit = async (payload: UpdateProductRequest) => {
    if (!id) return;
    await updateMutation.mutateAsync({ productId: id, data: payload });
    navigate(`/catalog/products/${id}`);
  };

  const isLoading = unitsLoading || (canReadCategories && categoriesLoading) || (mode === 'edit' && productLoading);

  return (
    <div className="space-y-6 max-w-5xl">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(backTarget)}
            className="-ml-2 mb-1 gap-1.5 text-xs text-slate-600"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>{mode === 'edit' ? 'Back to Product' : 'Back to Catalog'}</span>
          </Button>
          <div className="flex items-center gap-2">
            <Package className="h-6 w-6 text-slate-900" />
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              {mode === 'create' ? 'New Product' : `Edit ${product?.name ?? 'Product'}`}
            </h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            {mode === 'create'
              ? 'Define product classification, unit of measure, variants, SKUs, and barcodes.'
              : 'Update product metadata, category, measurement unit, and description.'}
          </p>
        </div>
      </div>

      {isLoading ? (
        <div
          role="status"
          className="flex h-48 items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white text-sm text-slate-500"
        >
          <Loader2 className="h-5 w-5 animate-spin" />
          <span>Loading catalog form data...</span>
        </div>
      ) : unitsError ? (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700 space-y-3">
          <div>{parseApiError(unitsErrorObj, 'Failed to load units of measure.').message}</div>
          <Button variant="outline" size="sm" onClick={() => void refetchUnits()} className="gap-1.5">
            <RefreshCw className="h-3.5 w-3.5" />
            <span>Retry</span>
          </Button>
        </div>
      ) : mode === 'edit' && (productError || !product) ? (
        <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700 space-y-3">
          <div>{parseApiError(productErrorObj, 'Product could not be loaded.').message}</div>
          <div className="flex items-center justify-center gap-2">
            <Button variant="outline" size="sm" onClick={() => void refetchProduct()} className="gap-1.5">
              <RefreshCw className="h-3.5 w-3.5" />
              <span>Retry</span>
            </Button>
            <Button variant="ghost" size="sm" onClick={() => navigate('/catalog/products')}>
              Back to Catalog
            </Button>
          </div>
        </div>
      ) : (
        <ProductForm
          mode={mode}
          initialProduct={product}
          categories={categories}
          units={units}
          isSubmitting={createMutation.isPending || updateMutation.isPending}
          onCancel={() => navigate(backTarget)}
          onCreateSubmit={handleCreateSubmit}
          onEditSubmit={handleEditSubmit}
        />
      )}
    </div>
  );
};
