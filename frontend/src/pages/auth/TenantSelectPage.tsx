import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../features/auth/model/useAuthStore';
import { useCurrentUser } from '../../features/auth/model/authQueries';
import { authApi } from '../../features/auth/api/authApi';
import { queryClient } from '../../shared/api/queryClient';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../shared/ui/card';
import { Button } from '../../shared/ui/button';
import { Store, CheckCircle, ArrowRight, LogOut, Loader2, AlertCircle } from 'lucide-react';
import { TenantMembership } from '../../entities/user/model/types';

export const TenantSelectPage: React.FC = () => {
  const navigate = useNavigate();
  const { activeTenant, setAccessToken, setActiveTenant, clearAuth } = useAuthStore();
  const { data: authData, isLoading: isUserLoading } = useCurrentUser();

  const user = authData?.user;
  const availableTenants = authData?.availableTenants || [];

  const [selectedId, setSelectedId] = useState<string | null>(activeTenant?.tenantId || null);
  const [loadingTenantId, setLoadingTenantId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSelectStore = async (tenant: TenantMembership) => {
    setSelectedId(tenant.tenantId);
    setLoadingTenantId(tenant.tenantId);
    setError(null);

    try {
      const response = await authApi.selectTenant({ tenantId: tenant.tenantId });
      setAccessToken(response.accessToken);
      setActiveTenant(tenant);

      // Clear all tenant-specific cached queries so previous store data never leaks
      queryClient.clear();

      navigate('/');
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.message || 'Failed to switch to store';
      setError(msg);
    } finally {
      setLoadingTenantId(null);
    }
  };

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } finally {
      clearAuth();
      queryClient.clear();
      navigate('/login');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
      <Card className="w-full max-w-xl shadow-lg border-slate-200">
        <CardHeader className="space-y-1">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-900 text-white">
                <Store className="h-5 w-5" />
              </div>
              <div>
                <CardTitle className="text-xl font-bold text-slate-900">Select Store</CardTitle>
                <CardDescription className="text-xs text-slate-500">
                  {user ? `Logged in as ${user.firstName} ${user.lastName} (${user.email})` : 'Loading profile...'}
                </CardDescription>
              </div>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={handleLogout}
              className="text-slate-600 hover:text-slate-900"
            >
              <LogOut className="mr-1.5 h-3.5 w-3.5" />
              Sign Out
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4 pt-2">
          {error && (
            <div
              role="alert"
              className="flex items-center gap-2 rounded-md bg-red-50 p-3 text-sm text-red-700 border border-red-200"
            >
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="space-y-2">
            <p className="text-sm font-medium text-slate-700">Choose a store to manage:</p>

            {isUserLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
              </div>
            ) : availableTenants.length === 0 ? (
              <div className="text-center py-8 rounded-lg border border-dashed border-slate-300 p-6">
                <Store className="mx-auto h-8 w-8 text-slate-400 mb-2" />
                <p className="text-sm font-medium text-slate-700">No stores found</p>
                <p className="text-xs text-slate-500 mt-1">
                  You are not currently assigned to any active store memberships. Contact your administrator or register a new store account.
                </p>
              </div>
            ) : (
              <div className="grid gap-2">
                {availableTenants.map((tenant) => {
                  const isCurrent = activeTenant?.tenantId === tenant.tenantId;
                  const isLoading = loadingTenantId === tenant.tenantId;

                  return (
                    <div
                      key={tenant.tenantId}
                      onClick={() => !isLoading && handleSelectStore(tenant)}
                      className={`flex items-center justify-between p-4 rounded-lg border transition-all cursor-pointer ${
                        selectedId === tenant.tenantId
                          ? 'border-slate-900 bg-slate-50 shadow-sm'
                          : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50/50'
                      }`}
                    >
                      <div className="flex items-center gap-3">
                        <div
                          className={`flex h-9 w-9 items-center justify-center rounded-md ${
                            selectedId === tenant.tenantId
                              ? 'bg-slate-900 text-white'
                              : 'bg-slate-100 text-slate-600'
                          }`}
                        >
                          <Store className="h-4 w-4" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <h4 className="text-sm font-semibold text-slate-900">{tenant.tenantName}</h4>
                            <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-mono font-medium text-slate-600">
                              {tenant.tenantCode}
                            </span>
                            {isCurrent && (
                              <span className="flex items-center gap-1 rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-medium text-emerald-800">
                                <CheckCircle className="h-3 w-3" />
                                Current
                              </span>
                            )}
                          </div>
                          <p className="text-xs text-slate-500">Status: {tenant.membershipStatus}</p>
                        </div>
                      </div>

                      <Button
                        size="sm"
                        variant={selectedId === tenant.tenantId ? 'default' : 'outline'}
                        disabled={isLoading}
                        className={selectedId === tenant.tenantId ? 'bg-slate-900 text-white' : ''}
                      >
                        {isLoading ? (
                          <Loader2 className="h-4 w-4 animate-spin" />
                        ) : (
                          <>
                            Enter
                            <ArrowRight className="ml-1.5 h-3.5 w-3.5" />
                          </>
                        )}
                      </Button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
