import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../../features/auth/model/useAuthStore';
import { ApiResponse, AuthResponse } from '../../entities/session/model/types';
import { queryClient } from './queryClient';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
  withCredentials: true,
});

// BroadcastChannel for cross-tab session coordination
const AUTH_CHANNEL_NAME = 'savdocrm_auth_channel';
let authChannel: BroadcastChannel | null = null;
try {
  if (typeof BroadcastChannel !== 'undefined') {
    authChannel = new BroadcastChannel(AUTH_CHANNEL_NAME);
    authChannel.onmessage = (event) => {
      if (event.data?.type === 'REFRESH_SUCCESS' && event.data.accessToken) {
        useAuthStore.getState().setAccessToken(event.data.accessToken);
        if (event.data.activeTenant) {
          useAuthStore.getState().setActiveTenant(event.data.activeTenant);
        }
        processQueue(null, event.data.accessToken);
      } else if (event.data?.type === 'AUTH_CLEARED') {
        useAuthStore.getState().clearAuth();
        queryClient.clear();
        processQueue(new Error('Session terminated by another tab'), null);
      }
    };
  }
} catch {
  // BroadcastChannel unavailable in non-browser environment
}

// Request Interceptor: Attach in-memory JWT Bearer token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().accessToken;
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Concurrency queue for token rotation within the same tab
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (token) {
      promise.resolve(token);
    } else {
      promise.reject(error);
    }
  });
  failedQueue = [];
};

/**
 * Coordinated cross-tab refresh using Web Locks API and single-flight queue.
 * Ensures only one tab across all browser windows performs /refresh.
 */
export async function executeCoordinatedRefresh(): Promise<string> {
  const currentToken = useAuthStore.getState().accessToken;

  const performHttpRefresh = async (): Promise<string> => {
    // Check if another tab refreshed while we were acquiring the lock
    const latestToken = useAuthStore.getState().accessToken;
    if (latestToken && latestToken !== currentToken) {
      return latestToken;
    }

    const refreshResponse = await axios.post<ApiResponse<AuthResponse>>(
      `${apiClient.defaults.baseURL}/auth/refresh`,
      {},
      { withCredentials: true }
    );

    const newAuth = refreshResponse.data.data;
    useAuthStore.getState().setAccessToken(newAuth.accessToken);
    if (newAuth.activeTenant) {
      useAuthStore.getState().setActiveTenant(newAuth.activeTenant);
    }

    try {
      if (authChannel) {
        authChannel.postMessage({
          type: 'REFRESH_SUCCESS',
          accessToken: newAuth.accessToken,
          activeTenant: newAuth.activeTenant,
        });
      }
    } catch {
      // Ignore broadcast errors
    }

    return newAuth.accessToken;
  };

  // If Web Locks API is supported, use it for cross-tab mutual exclusion
  if (typeof navigator !== 'undefined' && navigator.locks && typeof navigator.locks.request === 'function') {
    return navigator.locks.request('savdocrm_refresh_lock', async () => {
      return performHttpRefresh();
    });
  }

  // Fallback for environments without Web Locks API
  return performHttpRefresh();
}

// Response Interceptor: 401 refresh token rotation via HttpOnly cookie
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (!originalRequest) {
      return Promise.reject(error);
    }

    // Do not attempt refresh on auth endpoints (login, register, refresh, logout)
    if (
      originalRequest.url?.includes('/auth/login') ||
      originalRequest.url?.includes('/auth/register') ||
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/logout')
    ) {
      return Promise.reject(error);
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((newToken) => {
            if (originalRequest.headers) {
              originalRequest.headers.Authorization = `Bearer ${newToken}`;
            }
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const newAccessToken = await executeCoordinatedRefresh();

        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
        }

        processQueue(null, newAccessToken);
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        useAuthStore.getState().clearAuth();
        queryClient.clear();

        try {
          if (authChannel) {
            authChannel.postMessage({ type: 'AUTH_CLEARED' });
          }
        } catch {
          // Ignore
        }

        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
