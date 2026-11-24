import { apiClient } from '@/shared/api/client';
import { ApiResponse } from '@/entities/session/model/types';
import { Branch, CreateBranchRequest } from '../model/types';

export const branchApi = {
  getBranches: async (): Promise<Branch[]> => {
    const response = await apiClient.get<ApiResponse<Branch[]>>('/branches');
    return response.data.data;
  },

  getBranchById: async (id: string): Promise<Branch> => {
    const response = await apiClient.get<ApiResponse<Branch>>(`/branches/${id}`);
    return response.data.data;
  },

  createBranch: async (data: CreateBranchRequest): Promise<Branch> => {
    const response = await apiClient.post<ApiResponse<Branch>>('/branches', data);
    return response.data.data;
  },
};
