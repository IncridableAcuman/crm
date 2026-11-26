import { apiClient } from '@/shared/api/client';
import { ApiResponse } from '@/entities/session/model/types';
import { SystemHealth } from '../model/types';

export const systemApi = {
  getHealth: async (): Promise<SystemHealth> => {
    const response = await apiClient.get<ApiResponse<SystemHealth>>('/health');
    return response.data.data;
  },
};
