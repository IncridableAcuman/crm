import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryProvider } from '@/app/providers/QueryProvider';
import { HomePage } from '@/pages/home/HomePage';
import { LoginPage } from '@/pages/auth/LoginPage';
import { RegisterPage } from '@/pages/auth/RegisterPage';
import { TenantSelectPage } from '@/pages/auth/TenantSelectPage';
import { ProtectedRoute } from '@/app/routes/ProtectedRoute';

export const App: React.FC = () => {
  return (
    <QueryProvider>
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
            <Route path="/" element={<HomePage />} />
            <Route path="/dashboard" element={<HomePage />} />
          </Route>

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryProvider>
  );
};
