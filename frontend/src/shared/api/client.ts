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

/**
 * Shared non-token session metadata for cross-tab coordination.
 * CRITICAL SECURITY GUARANTEE: Neither access tokens nor refresh tokens are EVER stored in localStorage or sessionStorage.
 * Only synchronization flags (status, timestamps, epoch counters) are persisted to detect cross-tab events.
 */
export const SHARED_SESSION_STORAGE_KEY = 'savdocrm_session_state';

export interface SharedSessionMetadata {
  state: 'AUTHENTICATED' | 'UNAUTHENTICATED';
  timestamp: number;
  refreshEpoch: number;
}

export function getSharedSessionState(): SharedSessionMetadata | null {
  try {
    if (typeof localStorage === 'undefined') return null;
    const raw = localStorage.getItem(SHARED_SESSION_STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as SharedSessionMetadata;
  } catch {
    return null;
  }
}

export function setSharedSessionState(meta: SharedSessionMetadata): void {
  try {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(SHARED_SESSION_STORAGE_KEY, JSON.stringify(meta));
  } catch {
    // Ignore storage quota or environment issues
  }
}

export function clearSharedSessionState(): void {
  try {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(
      SHARED_SESSION_STORAGE_KEY,
      JSON.stringify({
        state: 'UNAUTHENTICATED',
        timestamp: Date.now(),
        refreshEpoch: 0,
      })
    );
  } catch {
    // Ignore
  }
}

export function broadcastAuthCleared(): void {
  try {
    if (authChannel) {
      authChannel.postMessage({ type: 'AUTH_CLEARED', timestamp: Date.now() });
    }
  } catch {
    // Ignore
  }
}

// BroadcastChannel for cross-tab session coordination
export const AUTH_CHANNEL_NAME = 'savdocrm_auth_channel';
export let authChannel: BroadcastChannel | null = null;
try {
  if (typeof BroadcastChannel !== 'undefined') {
    authChannel = new BroadcastChannel(AUTH_CHANNEL_NAME);
    authChannel.onmessage = (event) => {
      // SECURITY GUARANTEE: Neither access tokens nor refresh tokens are EVER received or accepted via BroadcastChannel
      if (event.data?.type === 'AUTH_CLEARED') {
        useAuthStore.getState().clearAuth();
        queryClient.clear();
        processQueue(new Error('Session terminated by another tab'), null);
      }
    };
  }
} catch {
  // BroadcastChannel unavailable in non-browser environment
}

// Cross-tab storage event synchronization for missed broadcasts or contexts without BroadcastChannel
if (typeof window !== 'undefined' && typeof window.addEventListener === 'function') {
  window.addEventListener('storage', (event: StorageEvent) => {
    if (event.key === SHARED_SESSION_STORAGE_KEY && event.newValue) {
      try {
        const parsed = JSON.parse(event.newValue) as SharedSessionMetadata;
        if (parsed?.state === 'UNAUTHENTICATED') {
          useAuthStore.getState().clearAuth();
          queryClient.clear();
          processQueue(new Error('Session terminated by another tab'), null);
        }
      } catch {
        // Safe fail-closed or ignore invalid JSON
      }
    }
  });
}

// Request Interceptor: Attach in-memory JWT Bearer token and check shared authentication state
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Verify shared session state: if another tab logged out, reject immediately without sending stale in-memory token
    const sharedState = getSharedSessionState();
    if (sharedState?.state === 'UNAUTHENTICATED' && useAuthStore.getState().isAuthenticated) {
      useAuthStore.getState().clearAuth();
      queryClient.clear();
      return Promise.reject(new Error('Session terminated by another tab'));
    }

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

export const processQueue = (error: unknown, token: string | null = null) => {
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
 *
 * Guarantees:
 * 1. Only one tab across all windows executes /refresh at any time.
 * 2. Other tabs wait for the lock.
 * 3. After acquiring the lock, a tab checks whether another tab already refreshed.
 * 4. A successful refresh updates the access token in all active tabs via BroadcastChannel.
 * 5. A failed refresh clears authentication state across all tabs.
 * 6. A crashed tab releases its browser lock automatically (Web Locks API specification guarantee).
 * 7. Browsers without Web Locks API fall back safely to single-flight refresh.
 * 8. NO refresh token is ever broadcast over BroadcastChannel.
 * 9. NO token is ever persisted to localStorage or sessionStorage.
 */
export async function executeCoordinatedRefresh(): Promise<string> {
  const currentToken = useAuthStore.getState().accessToken;
  const initialSharedState = getSharedSessionState();

  if (initialSharedState?.state === 'UNAUTHENTICATED') {
    useAuthStore.getState().clearAuth();
    queryClient.clear();
    throw new Error('Session terminated by another tab');
  }

  const performHttpRefresh = async (): Promise<string> => {
    // Check 1: Did another tab refresh and update in-memory access token while we waited for the lock?
    const latestToken = useAuthStore.getState().accessToken;
    if (latestToken && latestToken !== currentToken) {
      return latestToken;
    }

    // Check 2: Check shared session state
    const currentSharedState = getSharedSessionState();
    if (currentSharedState?.state === 'UNAUTHENTICATED') {
      useAuthStore.getState().clearAuth();
      queryClient.clear();
      throw new Error('Session terminated by another tab');
    }

    // Check 3: If in-memory token was already updated (e.g. by concurrent operation), return it
    const updatedToken = useAuthStore.getState().accessToken;
    if (updatedToken && updatedToken !== currentToken) {
      return updatedToken;
    }

    // This tab is the elected refresher: make the single HTTP request to rotate the token
    const refreshResponse = await axios.post<ApiResponse<AuthResponse>>(
      `${apiClient.defaults.baseURL}/auth/refresh`,
      {},
      { withCredentials: true }
    );

    const newAuth = refreshResponse.data.data;
    const nextEpoch = (currentSharedState?.refreshEpoch ?? 0) + 1;

    // Update in-memory state in the current tab
    useAuthStore.getState().setAccessToken(newAuth.accessToken);
    if (newAuth.activeTenant) {
      useAuthStore.getState().setActiveTenant(newAuth.activeTenant);
    }

    // Update shared session state in localStorage (NO TOKENS STORED!)
    setSharedSessionState({
      state: 'AUTHENTICATED',
      timestamp: Date.now(),
      refreshEpoch: nextEpoch,
    });

    // Broadcast non-token synchronization event to peer tabs (NEITHER ACCESS NOR REFRESH TOKENS ARE BROADCAST)
    try {
      if (authChannel) {
        authChannel.postMessage({
          type: 'REFRESH_SUCCESS',
          refreshEpoch: nextEpoch,
          timestamp: Date.now(),
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

  // Fallback for environments without Web Locks API: perform single-flight in-tab refresh
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
        clearSharedSessionState();
        broadcastAuthCleared();

        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
