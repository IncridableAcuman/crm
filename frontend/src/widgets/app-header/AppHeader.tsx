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
  Menu,
  X,
} from 'lucide-react';

interface AppHeaderProps {
  mobileMenuOpen?: boolean;
  onToggleMobileMenu?: () => void;
}

export const AppHeader: React.FC<AppHeaderProps> = ({
  mobileMenuOpen = false,
  onToggleMobileMenu,
}) => {
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
    <header className="sticky top-0 z-40 flex h-16 w-full items-center justify-between border-b border-slate-200 bg-white/95 px-4 sm:px-6 backdrop-blur supports-[backdrop-filter]:bg-white/60">
      <div className="flex items-center gap-3">
        {onToggleMobileMenu && (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="md:hidden h-9 w-9 text-slate-700"
            onClick={onToggleMobileMenu}
            aria-label={mobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'}
            aria-expanded={mobileMenuOpen}
          >
            {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </Button>
        )}

        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-slate-900 text-white shadow-sm shrink-0">
            <Store className="h-5 w-5" />
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="font-bold text-slate-900 text-base leading-none truncate max-w-[140px] sm:max-w-none">
                {activeTenant ? activeTenant.tenantName : 'SAVDOCRM'}
              </span>
              {activeTenant && (
                <Badge variant="outline" className="hidden sm:inline-flex font-mono text-[11px] px-1.5 py-0">
                  {activeTenant.tenantCode}
                </Badge>
              )}
            </div>
            <span className="hidden sm:inline text-[11px] text-slate-500">Retail SaaS Platform</span>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 sm:gap-3">
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
          aria-label="Switch Store"
        >
          <ArrowRightLeft className="h-3.5 w-3.5 md:mr-1.5" />
          <span className="hidden md:inline">Switch Store</span>
        </Button>

        <Button
          variant="outline"
          size="sm"
          onClick={handleLogout}
          className="text-xs h-8 text-red-600 hover:text-red-700 hover:bg-red-50 border-red-100"
          aria-label="Sign Out"
        >
          <LogOut className="h-3.5 w-3.5 md:mr-1.5" />
          <span className="hidden md:inline">Sign Out</span>
        </Button>
      </div>
    </header>
  );
};
