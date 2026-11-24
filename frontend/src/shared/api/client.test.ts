import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios from 'axios';
import {
  apiClient,
  executeCoordinatedRefresh,
  SHARED_SESSION_STORAGE_KEY,
  getSharedSessionState,
  clearSharedSessionState,
  authChannel,
} from './client';
import { useAuthStore } from '../../features/auth/model/useAuthStore';
import { queryClient } from './queryClient';

describe('HTTP Client & Cross-Tab Coordination', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    useAuthStore.getState().clearAuth();
    queryClient.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('should have withCredentials enabled by default for HttpOnly cookie propagation', () => {
    expect(apiClient.defaults.withCredentials).toBe(true);
  });

  it('should attach in-memory access token as Authorization Bearer header', async () => {
    useAuthStore.getState().setAccessToken('mock-in-memory-token');

    const requestHandler = (apiClient.interceptors.request as any).handlers[0].fulfilled;
    const config = await requestHandler({ headers: {} });

    expect(config.headers.Authorization).toBe('Bearer mock-in-memory-token');
  });

  it('should reject and clear state if shared state indicates session is unauthenticated (missed broadcast protection)', async () => {
    useAuthStore.getState().setAccessToken('stale-token');
    clearSharedSessionState(); // sets state to UNAUTHENTICATED

    const requestHandler = (apiClient.interceptors.request as any).handlers[0].fulfilled;

    await expect(requestHandler({ headers: {} })).rejects.toThrow('Session terminated by another tab');
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });

  it('should clear in-memory auth state, query cache, and shared session state when refresh fails', async () => {
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
    expect(getSharedSessionState()?.state).toBe('UNAUTHENTICATED');
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
    expect(getSharedSessionState()?.state).toBe('AUTHENTICATED');
  });

  it('should reuse refreshed token if another tab refreshed while waiting for Web Lock', async () => {
    const mockPost = vi.spyOn(axios, 'post');

    // Simulate Tab B waiting: Tab A finished and updated useAuthStore
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

    // Must NOT call /refresh HTTP endpoint because token was already updated
    expect(mockPost).not.toHaveBeenCalled();
    expect(token).toBe('token-refreshed-by-tab-a');
  });

  it('should fallback safely when Web Locks API is unsupported', async () => {
    vi.spyOn(axios, 'post').mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          accessToken: 'fallback-token-no-locks',
          tokenType: 'Bearer',
          expiresIn: 900,
        },
      },
    });

    // Remove navigator.locks to simulate unsupported environment
    Object.defineProperty(globalThis, 'navigator', {
      value: {},
      writable: true,
      configurable: true,
    });

    const token = await executeCoordinatedRefresh();
    expect(token).toBe('fallback-token-no-locks');
    expect(useAuthStore.getState().accessToken).toBe('fallback-token-no-locks');
  });

  it('should guarantee NO token persistence in localStorage or sessionStorage', async () => {
    vi.spyOn(axios, 'post').mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          accessToken: 'super-secret-jwt-token',
          refreshToken: null,
          tokenType: 'Bearer',
          expiresIn: 900,
        },
      },
    });

    await executeCoordinatedRefresh();

    // Verify localStorage has only session synchronization metadata and NO tokens
    const rawStorage = localStorage.getItem(SHARED_SESSION_STORAGE_KEY);
    expect(rawStorage).toBeDefined();
    expect(rawStorage).not.toContain('super-secret-jwt-token');
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('token')).toBeNull();
    expect(sessionStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('refreshToken')).toBeNull();
  });

  it('should broadcast REFRESH_SUCCESS without ever broadcasting access tokens or refresh tokens', async () => {
    const postMessageSpy = vi.fn();
    if (authChannel) {
      authChannel.postMessage = postMessageSpy;
    }

    vi.spyOn(axios, 'post').mockResolvedValueOnce({
      data: {
        success: true,
        data: {
          accessToken: 'secret-in-memory-access-token-123',
          refreshToken: null,
          tokenType: 'Bearer',
          expiresIn: 900,
        },
      },
    });

    await executeCoordinatedRefresh();

    if (authChannel) {
      expect(postMessageSpy).toHaveBeenCalledTimes(1);
      const firstCall = postMessageSpy.mock.calls[0];
      expect(firstCall).toBeDefined();
      const broadcastData = firstCall![0];
      expect(broadcastData.type).toBe('REFRESH_SUCCESS');
      // CRITICAL SECURITY INVARIANT: Neither access tokens nor refresh tokens are EVER broadcast
      expect(broadcastData.accessToken).toBeUndefined();
      expect(broadcastData.refreshToken).toBeUndefined();
      expect(broadcastData.refreshEpoch).toBeDefined();
    }
  });

  it('should clear in-memory auth and query cache when storage event signals UNAUTHENTICATED', () => {
    useAuthStore.getState().setAccessToken('active-token-to-be-cleared');
    queryClient.setQueryData(['cached-key'], { data: 123 });

    // Simulate cross-tab storage event
    const storageEvent = new StorageEvent('storage', {
      key: SHARED_SESSION_STORAGE_KEY,
      newValue: JSON.stringify({
        state: 'UNAUTHENTICATED',
        timestamp: Date.now(),
        refreshEpoch: 0,
      }),
    });

    window.dispatchEvent(storageEvent);

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(queryClient.getQueryData(['cached-key'])).toBeUndefined();
  });

  it('should allow requests after setSharedSessionState restores AUTHENTICATED state', async () => {
    // 1. Prior logout set state to UNAUTHENTICATED
    clearSharedSessionState();

    // 2. User logs in, updating in-memory token and restoring AUTHENTICATED shared state
    useAuthStore.getState().setAccessToken('freshly-logged-in-token');
    localStorage.setItem(
      SHARED_SESSION_STORAGE_KEY,
      JSON.stringify({
        state: 'AUTHENTICATED',
        timestamp: Date.now(),
        refreshEpoch: 0,
      })
    );

    const requestHandler = (apiClient.interceptors.request as any).handlers[0].fulfilled;
    const config = await requestHandler({ headers: {} });

    expect(config.headers.Authorization).toBe('Bearer freshly-logged-in-token');
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
  });
});
