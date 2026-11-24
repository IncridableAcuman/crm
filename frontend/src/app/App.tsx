import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryProvider } from '@/app/providers/QueryProvider';
import { AuthInitializer } from '@/features/auth/ui/AuthInitializer';
import { HomePage } from '@/pages/home/HomePage';
import { LoginPage } from '@/pages/auth/LoginPage';
import { RegisterPage } from '@/pages/auth/RegisterPage';
import { TenantSelectPage } from '@/pages/auth/TenantSelectPage';
import { BranchesPage } from '@/pages/branches/BranchesPage';
import { RolesPage } from '@/pages/roles/RolesPage';
import { RoleAssignmentsPage } from '@/pages/assignments/RoleAssignmentsPage';
import { ProtectedRoute } from '@/app/routes/ProtectedRoute';
import { AppLayout } from '@/widgets/layout/AppLayout';

export const App: React.FC = () => {
  return (
    <QueryProvider>
      <AuthInitializer>
        <BrowserRouter>
          <Routes>
            {/* Public Routes */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Authenticated route without mandatory tenant selection */}
            <Route element={<ProtectedRoute requireTenant={false} />}>
              <Route path="/select-tenant" element={<TenantSelectPage />} />
            </Route>

            {/* Fully protected routes requiring active tenant store */}
            <Route element={<ProtectedRoute requireTenant={true} />}>
              <Route element={<AppLayout />}>
                <Route path="/" element={<HomePage />} />
                <Route path="/dashboard" element={<HomePage />} />
                <Route path="/branches" element={<BranchesPage />} />
                <Route path="/roles" element={<RolesPage />} />
                <Route path="/assignments" element={<RoleAssignmentsPage />} />
              </Route>
            </Route>

            {/* Fallback */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthInitializer>
    </QueryProvider>
  );
};
