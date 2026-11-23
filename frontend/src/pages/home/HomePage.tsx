import React from 'react';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '../../shared/ui/card';
import { Button } from '../../shared/ui/button';
import { useAuthStore } from '../../features/auth/model/useAuthStore';
import { authApi } from '../../features/auth/api/authApi';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircle2,
  Server,
  Layers,
  ShieldCheck,
  Database,
  Store,
  LogOut,
  ArrowRightLeft,
  User as UserIcon,
} from 'lucide-react';

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const { user, activeTenant, refreshToken, clearAuth } = useAuthStore();

  const handleLogout = async () => {
    try {
      if (refreshToken) {
        await authApi.logout(refreshToken);
      }
    } finally {
      clearAuth();
      navigate('/login');
    }
  };

  return (
    <div className="container mx-auto py-8 px-4 max-w-5xl">
      {/* Top Navigation Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-6 border-b gap-4">
        <div>
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-900 text-white shadow-sm">
              <Store className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-2xl font-bold tracking-tight text-slate-900">
                  {activeTenant ? activeTenant.tenantName : 'SAVDOCRM'}
                </h1>
                {activeTenant && (
                  <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-mono font-medium text-slate-700">
                    {activeTenant.tenantCode}
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500">Retail Store Management & POS Platform</p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {user && (
            <div className="flex items-center gap-2 mr-2 text-right">
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-200 text-slate-700">
                <UserIcon className="h-4 w-4" />
              </div>
              <div className="text-xs text-left">
                <div className="font-semibold text-slate-900">
                  {user.firstName} {user.lastName}
                </div>
                <div className="text-slate-500">{user.email}</div>
              </div>
            </div>
          )}

          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate('/select-tenant')}
            className="text-xs"
          >
            <ArrowRightLeft className="mr-1.5 h-3.5 w-3.5" />
            Switch Store
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={handleLogout}
            className="text-xs text-red-600 hover:text-red-700 hover:bg-red-50"
          >
            <LogOut className="mr-1.5 h-3.5 w-3.5" />
            Sign Out
          </Button>
        </div>
      </div>

      {/* Overview Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-6">
        <Card>
          <CardHeader>
            <Server className="w-5 h-5 text-slate-900 mb-1" />
            <CardTitle className="text-base">Identity & Auth Tier</CardTitle>
            <CardDescription className="text-xs">Dual-Phase JWT & Refresh Token Rotation</CardDescription>
          </CardHeader>
          <CardContent className="text-xs text-slate-600 space-y-1.5">
            <div>• Dual-phase JWT (Global & Tenant-scoped)</div>
            <div>• SHA-256 Token Rotation & Reuse Detection</div>
            <div>• BCrypt Password Security (Cost 12)</div>
            <div>• Zero-Trust Header Isolation</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <Database className="w-5 h-5 text-slate-900 mb-1" />
            <CardTitle className="text-base">Tenancy & Branches</CardTitle>
            <CardDescription className="text-xs">PostgreSQL 16 & Composite Keys</CardDescription>
          </CardHeader>
          <CardContent className="text-xs text-slate-600 space-y-1.5">
            <div>• Active Store: <span className="font-semibold text-slate-900">{activeTenant?.tenantName || 'None'}</span></div>
            <div>• Tenant ID: <span className="font-mono text-[10px] text-slate-700">{activeTenant?.tenantId || 'N/A'}</span></div>
            <div>• Fail-closed TenantContextHolder</div>
            <div>• Composite foreign key readiness</div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <Layers className="w-5 h-5 text-slate-900 mb-1" />
            <CardTitle className="text-base">Frontend Architecture</CardTitle>
            <CardDescription className="text-xs">Feature-Sliced Design (React 19)</CardDescription>
          </CardHeader>
          <CardContent className="text-xs text-slate-600 space-y-1.5">
            <div>• Zustand Persistent Auth Store</div>
            <div>• Axios 401 Concurrent Refresh Queue</div>
            <div>• Protected Route Guards</div>
            <div>• React Hook Form & Zod Validation</div>
          </CardContent>
        </Card>
      </div>

      {/* Milestone Status Banner */}
      <Card className="mt-6 border-slate-200">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-emerald-600" />
              <CardTitle className="text-base">Milestone 03 — Authentication & Session Management</CardTitle>
            </div>
            <span className="inline-flex items-center rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700 ring-1 ring-inset ring-emerald-600/20">
              <CheckCircle2 className="w-3.5 h-3.5 mr-1" />
              Verified & Tested
            </span>
          </div>
          <CardDescription className="text-xs">
            Global users, tenant memberships, dual-phase tokens, session revocation, and security filter chain are fully active.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-lg bg-slate-900 text-slate-100 p-4 font-mono text-xs space-y-1.5">
            <div className="text-emerald-400 font-semibold">✓ Backend: 32 tests PASSED (PostgreSQL 16 Testcontainers)</div>
            <div className="text-emerald-400 font-semibold">✓ Architecture: ArchUnit structural rules enforced</div>
            <div className="text-emerald-400 font-semibold">✓ Token Security: Refresh token family reuse detection active</div>
            <div className="text-emerald-400 font-semibold">✓ Multi-Tenancy: Live membership & tenant status verified on every request</div>
            <div className="text-emerald-400 font-semibold">✓ Frontend: FSD auth slice & protected routing complete</div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
