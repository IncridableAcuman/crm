import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../features/auth/model/useAuthStore';
import { Loader2 } from 'lucide-react';

interface ProtectedRouteProps {
  requireTenant?: boolean;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ requireTenant = true }) => {
  const { isAuthenticated, isTenantSelected, isInitialized } = useAuthStore();
  const location = useLocation();

  if (!isInitialized) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <Loader2 className="h-8 w-8 animate-spin text-slate-700" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requireTenant && !isTenantSelected) {
    return <Navigate to="/select-tenant" replace />;
  }

  return <Outlet />;
};
