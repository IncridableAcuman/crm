import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../features/auth/model/useAuthStore';

interface ProtectedRouteProps {
  requireTenant?: boolean;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ requireTenant = true }) => {
  const { isAuthenticated, isTenantSelected } = useAuthStore();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requireTenant && !isTenantSelected) {
    return <Navigate to="/select-tenant" replace />;
  }

  return <Outlet />;
};
