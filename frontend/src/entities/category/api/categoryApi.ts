import { apiClient } from '@/shared/api/client';
import { ApiResponse } from '@/entities/session/model/types';
import {
  Category,
  CategoryTreeNode,
  CreateCategoryRequest,
  MoveCategoryRequest,
  UpdateCategoryRequest,
} from '../model/types';

export interface ListCategoriesParams {
  includeInactive?: boolean;
  branchId?: string;
}

export const categoryApi = {
  listCategories: async (params: ListCategoriesParams = {}): Promise<Category[]> => {
    const queryParams: Record<string, string | boolean> = {
      view: 'flat',
      includeInactive: Boolean(params.includeInactive),
    };
    if (params.branchId) {
      queryParams.branchId = params.branchId;
    }
    const response = await apiClient.get<ApiResponse<Category[]>>('/categories', {
      params: queryParams,
    });
    return response.data.data;
  },

  getCategoryTree: async (params: ListCategoriesParams = {}): Promise<CategoryTreeNode[]> => {
    const queryParams: Record<string, string | boolean> = {
      includeInactive: Boolean(params.includeInactive),
    };
    if (params.branchId) {
      queryParams.branchId = params.branchId;
    }
    const response = await apiClient.get<ApiResponse<CategoryTreeNode[]>>('/categories/tree', {
      params: queryParams,
    });
    return response.data.data;
  },

  getCategory: async (categoryId: string, branchId?: string): Promise<Category> => {
    const params = branchId ? { branchId } : undefined;
    const response = await apiClient.get<ApiResponse<Category>>(`/categories/${categoryId}`, {
      params,
    });
    return response.data.data;
  },

  createCategory: async (data: CreateCategoryRequest): Promise<Category> => {
    const response = await apiClient.post<ApiResponse<Category>>('/categories', data);
    return response.data.data;
  },

  updateCategory: async (categoryId: string, data: UpdateCategoryRequest): Promise<Category> => {
    const response = await apiClient.put<ApiResponse<Category>>(`/categories/${categoryId}`, data);
    return response.data.data;
  },

  moveCategory: async (categoryId: string, data: MoveCategoryRequest): Promise<Category> => {
    const response = await apiClient.patch<ApiResponse<Category>>(
      `/categories/${categoryId}/move`,
      data
    );
    return response.data.data;
  },

  updateCategoryStatus: async (categoryId: string, isActive: boolean): Promise<Category> => {
    const response = await apiClient.patch<ApiResponse<Category>>(
      `/categories/${categoryId}/status`,
      { isActive }
    );
    return response.data.data;
  },

  activateCategory: async (categoryId: string): Promise<Category> => {
    const response = await apiClient.post<ApiResponse<Category>>(
      `/categories/${categoryId}/activate`
    );
    return response.data.data;
  },

  deactivateCategory: async (categoryId: string): Promise<Category> => {
    const response = await apiClient.post<ApiResponse<Category>>(
      `/categories/${categoryId}/deactivate`
    );
    return response.data.data;
  },

  deleteCategory: async (categoryId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<void>>(`/categories/${categoryId}`);
  },
};
