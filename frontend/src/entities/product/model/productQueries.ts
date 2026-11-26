import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { useCatalogReadBranchScope } from '@/entities/permission';
import { productApi } from '../api/productApi';
import {
  AddBarcodeRequest,
  ConvertProductToVariableRequest,
  CreateProductRequest,
  CreateVariantRequest,
  ProductListParams,
  ProductStatus,
  UpdateBarcodeRequest,
  UpdateProductRequest,
  UpdateVariantRequest,
} from '../model/types';

export const productKeys = {
  all: (tenantId: string | undefined) => ['catalog', tenantId, 'products'] as const,
  list: (tenantId: string | undefined, params: ProductListParams) =>
    [...productKeys.all(tenantId), 'list', params] as const,
  detail: (tenantId: string | undefined, productId: string, branchId?: string) =>
    [...productKeys.all(tenantId), 'detail', productId, branchId ?? null] as const,
  lookup: (tenantId: string | undefined, code: string, branchId?: string) =>
    [...productKeys.all(tenantId), 'lookup', code, branchId ?? null] as const,
};

export function useProducts(params: ProductListParams = {}, options?: { enabled?: boolean }) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const scope = useCatalogReadBranchScope('catalog.products.read', params.branchId);
  const effectiveParams: ProductListParams = {
    ...params,
    branchId: scope.resolvedBranchId,
  };

  return useQuery({
    queryKey: productKeys.list(activeTenant?.tenantId, effectiveParams),
    queryFn: () => productApi.listProducts(effectiveParams),
    enabled:
      isAuthenticated && !!activeTenant && scope.canRead && (options?.enabled ?? true),
    staleTime: 60 * 1000,
  });
}

export function useProduct(
  productId: string,
  options?: { branchId?: string; enabled?: boolean }
) {
  const activeTenant = useAuthStore((s) => s.activeTenant);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const scope = useCatalogReadBranchScope('catalog.products.read', options?.branchId);

  return useQuery({
    queryKey: productKeys.detail(activeTenant?.tenantId, productId, scope.resolvedBranchId),
    queryFn: () => productApi.getProduct(productId, scope.resolvedBranchId),
    enabled:
      isAuthenticated &&
      !!activeTenant &&
      !!productId &&
      scope.canRead &&
      (options?.enabled ?? true),
    staleTime: 60 * 1000,
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (data: CreateProductRequest) => productApi.createProduct(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useUpdateProduct() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ productId, data }: { productId: string; data: UpdateProductRequest }) =>
      productApi.updateProduct(productId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useUpdateProductStatus() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ productId, status }: { productId: string; status: ProductStatus }) =>
      productApi.updateProductStatus(productId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useArchiveProduct() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (productId: string) => productApi.archiveProduct(productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useRestoreProduct() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (productId: string) => productApi.restoreProduct(productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useConvertProductToVariable() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({
      productId,
      data,
    }: {
      productId: string;
      data: ConvertProductToVariableRequest;
    }) => productApi.convertToVariable(productId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useDeleteDraftProduct() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: (productId: string) => productApi.deleteDraftProduct(productId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useAddVariant() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ productId, data }: { productId: string; data: CreateVariantRequest }) =>
      productApi.addVariant(productId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useUpdateVariant() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({
      productId,
      variantId,
      data,
    }: {
      productId: string;
      variantId: string;
      data: UpdateVariantRequest;
    }) => productApi.updateVariant(productId, variantId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useChangeDefaultVariant() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({ productId, variantId }: { productId: string; variantId: string }) =>
      productApi.changeDefaultVariant(productId, variantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useUpdateVariantStatus() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({
      productId,
      variantId,
      isActive,
    }: {
      productId: string;
      variantId: string;
      isActive: boolean;
    }) => productApi.updateVariantStatus(productId, variantId, isActive),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useAddBarcode() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({
      productId,
      variantId,
      data,
    }: {
      productId: string;
      variantId: string;
      data: AddBarcodeRequest;
    }) => productApi.addBarcode(productId, variantId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useUpdateBarcode() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({
      productId,
      variantId,
      barcodeId,
      data,
    }: {
      productId: string;
      variantId: string;
      barcodeId: string;
      data: UpdateBarcodeRequest;
    }) => productApi.updateBarcode(productId, variantId, barcodeId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useSetPrimaryBarcode() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({
      productId,
      variantId,
      barcodeId,
    }: {
      productId: string;
      variantId: string;
      barcodeId: string;
    }) => productApi.setPrimaryBarcode(productId, variantId, barcodeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}

export function useRemoveBarcode() {
  const queryClient = useQueryClient();
  const activeTenant = useAuthStore((s) => s.activeTenant);

  return useMutation({
    mutationFn: ({
      productId,
      variantId,
      barcodeId,
    }: {
      productId: string;
      variantId: string;
      barcodeId: string;
    }) => productApi.removeBarcode(productId, variantId, barcodeId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: productKeys.all(activeTenant?.tenantId) });
    },
  });
}
