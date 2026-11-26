import { apiClient } from '@/shared/api/client';
import { ApiResponse, PageResponse } from '@/entities/session/model/types';
import {
  AddBarcodeRequest,
  ConvertProductToVariableRequest,
  CreateProductRequest,
  CreateVariantRequest,
  ProductBarcode,
  ProductDetail,
  ProductListParams,
  ProductStatus,
  ProductSummary,
  ProductVariant,
  UpdateBarcodeRequest,
  UpdateProductRequest,
  UpdateVariantRequest,
  VariantLookup,
} from '../model/types';

function cleanListParams(params: ProductListParams): Record<string, string | number | boolean> {
  const cleaned: Record<string, string | number | boolean> = {};
  if (params.search?.trim()) cleaned.search = params.search.trim();
  if (params.sku?.trim()) cleaned.sku = params.sku.trim();
  if (params.barcode?.trim()) cleaned.barcode = params.barcode.trim();
  if (params.categoryId) cleaned.categoryId = params.categoryId;
  if (params.includeSubcategories !== undefined) {
    cleaned.includeSubcategories = params.includeSubcategories;
  }
  if (params.unitId) cleaned.unitId = params.unitId;
  if (params.brand?.trim()) cleaned.brand = params.brand.trim();
  if (params.status) cleaned.status = params.status;
  if (params.productType) cleaned.productType = params.productType;
  if (params.isActive !== undefined) cleaned.isActive = params.isActive;
  if (params.includeArchived !== undefined) cleaned.includeArchived = params.includeArchived;
  if (params.branchId) cleaned.branchId = params.branchId;
  if (params.page !== undefined) cleaned.page = params.page;
  if (params.size !== undefined) cleaned.size = params.size;
  if (params.sort) cleaned.sort = params.sort;
  return cleaned;
}

export const productApi = {
  listProducts: async (params: ProductListParams = {}): Promise<PageResponse<ProductSummary>> => {
    const response = await apiClient.get<ApiResponse<PageResponse<ProductSummary>>>('/products', {
      params: cleanListParams(params),
    });
    return response.data.data;
  },

  lookupVariant: async (code: string, branchId?: string): Promise<VariantLookup> => {
    const params: Record<string, string> = { code: code.trim() };
    if (branchId) params.branchId = branchId;
    const response = await apiClient.get<ApiResponse<VariantLookup>>('/products/lookup', {
      params,
    });
    return response.data.data;
  },

  getProduct: async (productId: string, branchId?: string): Promise<ProductDetail> => {
    const params = branchId ? { branchId } : undefined;
    const response = await apiClient.get<ApiResponse<ProductDetail>>(`/products/${productId}`, {
      params,
    });
    return response.data.data;
  },

  createProduct: async (data: CreateProductRequest): Promise<ProductDetail> => {
    const response = await apiClient.post<ApiResponse<ProductDetail>>('/products', data);
    return response.data.data;
  },

  updateProduct: async (productId: string, data: UpdateProductRequest): Promise<ProductDetail> => {
    const response = await apiClient.put<ApiResponse<ProductDetail>>(
      `/products/${productId}`,
      data
    );
    return response.data.data;
  },

  updateProductStatus: async (productId: string, status: ProductStatus): Promise<ProductDetail> => {
    const response = await apiClient.patch<ApiResponse<ProductDetail>>(
      `/products/${productId}/status`,
      { status }
    );
    return response.data.data;
  },

  archiveProduct: async (productId: string): Promise<ProductDetail> => {
    const response = await apiClient.post<ApiResponse<ProductDetail>>(
      `/products/${productId}/archive`
    );
    return response.data.data;
  },

  restoreProduct: async (productId: string): Promise<ProductDetail> => {
    const response = await apiClient.post<ApiResponse<ProductDetail>>(
      `/products/${productId}/restore`
    );
    return response.data.data;
  },

  convertToVariable: async (
    productId: string,
    data: ConvertProductToVariableRequest
  ): Promise<ProductDetail> => {
    const response = await apiClient.post<ApiResponse<ProductDetail>>(
      `/products/${productId}/convert-to-variable`,
      data
    );
    return response.data.data;
  },

  deleteDraftProduct: async (productId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<void>>(`/products/${productId}`);
  },

  // Variants
  listVariants: async (productId: string, branchId?: string): Promise<ProductVariant[]> => {
    const params = branchId ? { branchId } : undefined;
    const response = await apiClient.get<ApiResponse<ProductVariant[]>>(
      `/products/${productId}/variants`,
      { params }
    );
    return response.data.data;
  },

  addVariant: async (productId: string, data: CreateVariantRequest): Promise<ProductVariant> => {
    const response = await apiClient.post<ApiResponse<ProductVariant>>(
      `/products/${productId}/variants`,
      data
    );
    return response.data.data;
  },

  updateVariant: async (
    productId: string,
    variantId: string,
    data: UpdateVariantRequest
  ): Promise<ProductVariant> => {
    const response = await apiClient.put<ApiResponse<ProductVariant>>(
      `/products/${productId}/variants/${variantId}`,
      data
    );
    return response.data.data;
  },

  changeDefaultVariant: async (productId: string, variantId: string): Promise<ProductVariant> => {
    const response = await apiClient.patch<ApiResponse<ProductVariant>>(
      `/products/${productId}/variants/${variantId}/default`
    );
    return response.data.data;
  },

  updateVariantStatus: async (
    productId: string,
    variantId: string,
    isActive: boolean
  ): Promise<ProductVariant> => {
    const response = await apiClient.patch<ApiResponse<ProductVariant>>(
      `/products/${productId}/variants/${variantId}/status`,
      { isActive }
    );
    return response.data.data;
  },

  // Barcodes
  listBarcodes: async (
    productId: string,
    variantId: string,
    branchId?: string
  ): Promise<ProductBarcode[]> => {
    const params = branchId ? { branchId } : undefined;
    const response = await apiClient.get<ApiResponse<ProductBarcode[]>>(
      `/products/${productId}/variants/${variantId}/barcodes`,
      { params }
    );
    return response.data.data;
  },

  addBarcode: async (
    productId: string,
    variantId: string,
    data: AddBarcodeRequest
  ): Promise<ProductBarcode> => {
    const response = await apiClient.post<ApiResponse<ProductBarcode>>(
      `/products/${productId}/variants/${variantId}/barcodes`,
      data
    );
    return response.data.data;
  },

  updateBarcode: async (
    productId: string,
    variantId: string,
    barcodeId: string,
    data: UpdateBarcodeRequest
  ): Promise<ProductBarcode> => {
    const response = await apiClient.put<ApiResponse<ProductBarcode>>(
      `/products/${productId}/variants/${variantId}/barcodes/${barcodeId}`,
      data
    );
    return response.data.data;
  },

  setPrimaryBarcode: async (
    productId: string,
    variantId: string,
    barcodeId: string
  ): Promise<ProductBarcode> => {
    const response = await apiClient.patch<ApiResponse<ProductBarcode>>(
      `/products/${productId}/variants/${variantId}/barcodes/${barcodeId}/primary`
    );
    return response.data.data;
  },

  removeBarcode: async (
    productId: string,
    variantId: string,
    barcodeId: string
  ): Promise<void> => {
    await apiClient.delete<ApiResponse<void>>(
      `/products/${productId}/variants/${variantId}/barcodes/${barcodeId}`
    );
  },
};
