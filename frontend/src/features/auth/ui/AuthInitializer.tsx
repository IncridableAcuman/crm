import React, { useEffect } from 'react';
import { useAuthStore } from '../model/useAuthStore';
import { authApi } from '../api/authApi';

interface AuthInitializerProps {
  children: React.ReactNode;
}

export const AuthInitializer: React.FC<AuthInitializerProps> = ({ children }) => {
  const { setAccessToken, setActiveTenant, setInitialized, isInitialized } = useAuthStore();

  useEffect(() => {
    let isMounted = true;

    const initializeAuth = async () => {
      try {
        const response = await authApi.refreshToken();
        if (isMounted) {
          setAccessToken(response.accessToken);
          if (response.activeTenant) {
            setActiveTenant(response.activeTenant);
          }
        }
      } catch {
        // No valid refresh token cookie exists or session is expired; remain unauthenticated
      } finally {
        if (isMounted) {
          setInitialized(true);
        }
      }
    };

    if (!isInitialized) {
      initializeAuth();
    }

    return () => {
      isMounted = false;
    };
  }, [isInitialized, setAccessToken, setActiveTenant, setInitialized]);

  return <>{children}</>;
};
