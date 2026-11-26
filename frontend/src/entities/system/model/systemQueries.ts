import { useQuery } from '@tanstack/react-query';
import { systemApi } from '../api/systemApi';

export const systemKeys = {
  all: ['system'] as const,
  health: () => [...systemKeys.all, 'health'] as const,
};

export function useSystemHealth(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: systemKeys.health(),
    queryFn: () => systemApi.getHealth(),
    enabled: options?.enabled ?? true,
    staleTime: 60 * 1000,
    retry: 1,
  });
}
