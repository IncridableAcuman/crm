import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios from 'axios';
import { apiClient, executeCoordinatedRefresh } from './client';
import { useAuthStore } from '../../features/auth/model/useAuthStore';
import { queryClient } from './queryClient';

describe('HTTP Client & Concurrent 401 Refresh Queue', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useAuthStore.getState().clearAuth();
    queryClient.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
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

  it('should execute coordinated refresh using Web Locks API and update auth state', async () => {
    const mockPost = vi.spyOn(axios, 'post').mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          accessToken: 'new-coordinated-token',
          refreshToken: null,
          tokenType: 'Bearer',
          expiresIn: 900,
        },
      },
    });

    // Mock navigator.locks
    const mockRequest = vi.fn().mockImplementation(async (_name, callback) => {
      return callback();
    });

    const originalNavigator = globalThis.navigator;
    Object.defineProperty(globalThis, 'navigator', {
      value: {
        ...originalNavigator,
        locks: { request: mockRequest },
      },
      writable: true,
      configurable: true,
    });

    const token = await executeCoordinatedRefresh();

    expect(mockRequest).toHaveBeenCalledWith('savdocrm_refresh_lock', expect.any(Function));
    expect(mockPost).toHaveBeenCalledTimes(1);
    expect(token).toBe('new-coordinated-token');
    expect(useAuthStore.getState().accessToken).toBe('new-coordinated-token');
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
  });

  it('should reuse refreshed token if another tab refreshed while waiting for Web Lock', async () => {
    const mockPost = vi.spyOn(axios, 'post');

    // Mock navigator.locks simulating Tab B waiting:
    // while waiting, Tab A finished and updated useAuthStore
    const mockRequest = vi.fn().mockImplementation(async (_name, callback) => {
      useAuthStore.getState().setAccessToken('token-refreshed-by-tab-a');
      return callback();
    });

    Object.defineProperty(globalThis, 'navigator', {
      value: {
        locks: { request: mockRequest },
      },
      writable: true,
      configurable: true,
    });

    const token = await executeCoordinatedRefresh();

    // Must NOT call /refresh HTTP endpoint because token was already updated!
    expect(mockPost).not.toHaveBeenCalled();
    expect(token).toBe('token-refreshed-by-tab-a');
  });
});
