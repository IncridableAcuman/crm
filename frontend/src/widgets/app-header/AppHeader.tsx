import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { useCurrentUser } from '@/features/auth/model/authQueries';
import { authApi } from '@/features/auth/api/authApi';
import { queryClient } from '@/shared/api/queryClient';
import { Button } from '@/shared/ui/button';
import { Badge } from '@/shared/ui/badge';
import {
  Store,
  ArrowRightLeft,
  LogOut,
  User as UserIcon,
} from 'lucide-react';

export const AppHeader: React.FC = () => {
  const navigate = useNavigate();
  const { activeTenant, clearAuth } = useAuthStore();
  const { data: authData } = useCurrentUser();
  const user = authData?.user;

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
    <header className="sticky top-0 z-40 flex h-16 w-full items-center justify-between border-b border-slate-200 bg-white/95 px-6 backdrop-blur supports-[backdrop-filter]:bg-white/60">
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-900 text-white shadow-sm">
            <Store className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-slate-900 text-base leading-none">
                {activeTenant ? activeTenant.tenantName : 'SAVDOCRM'}
              </span>
              {activeTenant && (
                <Badge variant="outline" className="font-mono text-[11px] px-1.5 py-0">
                  {activeTenant.tenantCode}
                </Badge>
              )}
            </div>
            <span className="text-[11px] text-slate-500">Retail SaaS Platform</span>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {user && (
          <div className="hidden sm:flex items-center gap-2 text-right">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-700 font-semibold text-xs border border-slate-200">
              {user.firstName?.[0] || <UserIcon className="h-4 w-4" />}
            </div>
            <div className="text-left text-xs">
              <div className="font-medium text-slate-900">
                {user.firstName} {user.lastName}
              </div>
              <div className="text-slate-500 text-[11px]">{user.email}</div>
            </div>
          </div>
        )}

        <Button
          variant="outline"
          size="sm"
          onClick={() => navigate('/select-tenant')}
          className="text-xs h-8"
        >
          <ArrowRightLeft className="mr-1.5 h-3.5 w-3.5" />
          <span className="hidden md:inline">Switch Store</span>
        </Button>

        <Button
          variant="outline"
          size="sm"
          onClick={handleLogout}
          className="text-xs h-8 text-red-600 hover:text-red-700 hover:bg-red-50 border-red-100"
        >
          <LogOut className="mr-1.5 h-3.5 w-3.5" />
          <span className="hidden md:inline">Sign Out</span>
        </Button>
      </div>
    </header>
  );
};
