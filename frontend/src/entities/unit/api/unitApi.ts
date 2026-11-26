import { apiClient } from '@/shared/api/client';
import { ApiResponse } from '@/entities/session/model/types';
import { UnitOfMeasure, CreateUnitRequest, UpdateUnitRequest } from '../model/types';

export interface ListUnitsParams {
  includeInactive?: boolean;
  branchId?: string;
}

export const unitApi = {
  listUnits: async (params: ListUnitsParams = {}): Promise<UnitOfMeasure[]> => {
    const queryParams: Record<string, string | boolean> = {
      includeInactive: Boolean(params.includeInactive),
    };
    if (params.branchId) {
      queryParams.branchId = params.branchId;
    }
    const response = await apiClient.get<ApiResponse<UnitOfMeasure[]>>('/units', {
      params: queryParams,
    });
    return response.data.data;
  },

  getUnit: async (unitId: string, branchId?: string): Promise<UnitOfMeasure> => {
    const params = branchId ? { branchId } : undefined;
    const response = await apiClient.get<ApiResponse<UnitOfMeasure>>(`/units/${unitId}`, { params });
    return response.data.data;
  },

  createUnit: async (data: CreateUnitRequest): Promise<UnitOfMeasure> => {
    const response = await apiClient.post<ApiResponse<UnitOfMeasure>>('/units', data);
    return response.data.data;
  },

  updateUnit: async (unitId: string, data: UpdateUnitRequest): Promise<UnitOfMeasure> => {
    const response = await apiClient.put<ApiResponse<UnitOfMeasure>>(`/units/${unitId}`, data);
    return response.data.data;
  },

  updateUnitStatus: async (unitId: string, isActive: boolean): Promise<UnitOfMeasure> => {
    const response = await apiClient.patch<ApiResponse<UnitOfMeasure>>(`/units/${unitId}/status`, {
      isActive,
    });
    return response.data.data;
  },

  deleteUnit: async (unitId: string): Promise<void> => {
    await apiClient.delete<ApiResponse<void>>(`/units/${unitId}`);
  },
};
