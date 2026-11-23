import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import { apiClient } from './client';
import { useAuthStore } from '../../features/auth/model/useAuthStore';
import { queryClient } from './queryClient';

describe('HTTP Client & Concurrent 401 Refresh Queue', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useAuthStore.getState().clearAuth();
    queryClient.clear();
  });

  it('should have withCredentials enabled by default for HttpOnly cookie propagation', () => {
    expect(apiClient.defaults.withCredentials).toBe(true);
  });

  it('should attach in-memory access token as Authorization Bearer header', async () => {
    useAuthStore.getState().setAccessToken('mock-in-memory-token');

    // Test request interceptor
    const requestHandler = (apiClient.interceptors.request as any).handlers[0].fulfilled;
    const config = await requestHandler({ headers: {} });

    expect(config.headers.Authorization).toBe('Bearer mock-in-memory-token');
  });

  it('should clear in-memory auth state and query cache when refresh fails', async () => {
    useAuthStore.getState().setAccessToken('old-expired-token');
    queryClient.setQueryData(['testData'], { key: 'value' });

    vi.spyOn(axios, 'post').mockRejectedValueOnce(new Error('Refresh cookie expired'));

    const responseErrorHandler = (apiClient.interceptors.response as any).handlers[0].rejected;

    const mock401Error = {
      response: { status: 401 },
      config: { url: '/branches', headers: {} },
    };

    await expect(responseErrorHandler(mock401Error)).rejects.toThrow();

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(queryClient.getQueryData(['testData'])).toBeUndefined();
  });
});
