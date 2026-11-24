import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  GitBranch,
  ShieldCheck,
  Users,
  ShoppingCart,
  Package,
  Boxes,
} from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { Badge } from '@/shared/ui/badge';
import { useHasPermission } from '@/entities/permission/model/permissionQueries';

interface NavItem {
  title: string;
  to: string;
  icon: React.ElementType;
  permission?: string;
  badge?: string;
}

export const AppSidebar: React.FC = () => {
  const canReadBranches = useHasPermission('branches.read');
  const canReadRoles = useHasPermission('identity.roles.read');

  const mainNavItems: NavItem[] = [
    {
      title: 'Dashboard',
      to: '/',
      icon: LayoutDashboard,
    },
    {
      title: 'Store Branches',
      to: '/branches',
      icon: GitBranch,
      permission: 'branches.read',
    },
    {
      title: 'Roles & Permissions',
      to: '/roles',
      icon: ShieldCheck,
      permission: 'identity.roles.read',
    },
    {
      title: 'Role Assignments',
      to: '/assignments',
      icon: Users,
      permission: 'identity.roles.read',
    },
  ];

  const comingSoonItems: NavItem[] = [
    {
      title: 'Point of Sale (POS)',
      to: '/pos',
      icon: ShoppingCart,
      badge: 'M06',
    },
    {
      title: 'Product Catalog',
      to: '/catalog',
      icon: Package,
      badge: 'M05',
    },
    {
      title: 'Stock & Inventory',
      to: '/inventory',
      icon: Boxes,
      badge: 'M07',
    },
  ];

  return (
    <aside className="w-64 border-r border-slate-200 bg-slate-50/50 min-h-[calc(100vh-4rem)] flex flex-col justify-between p-4">
      <div className="space-y-6">
        <div>
          <div className="px-3 mb-2 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">
            Store Management
          </div>
          <nav className="space-y-1">
            {mainNavItems.map((item) => {
              if (item.permission === 'branches.read' && !canReadBranches) {
                return null;
              }
              if (item.permission === 'identity.roles.read' && !canReadRoles) {
                return null;
              }

              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all',
                      isActive
                        ? 'bg-slate-900 text-white shadow-sm'
                        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                    )
                  }
                >
                  <Icon className="h-4 w-4" />
                  <span>{item.title}</span>
                </NavLink>
              );
            })}
          </nav>
        </div>

        <div>
          <div className="px-3 mb-2 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">
            Upcoming Modules
          </div>
          <div className="space-y-1">
            {comingSoonItems.map((item) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.title}
                  className="flex items-center justify-between rounded-lg px-3 py-2 text-sm text-slate-400 cursor-not-allowed select-none"
                >
                  <div className="flex items-center gap-3">
                    <Icon className="h-4 w-4 text-slate-300" />
                    <span>{item.title}</span>
                  </div>
                  {item.badge && (
                    <Badge variant="secondary" className="text-[10px] px-1.5 py-0 text-slate-400 bg-slate-100">
                      {item.badge}
                    </Badge>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Security Status Footer */}
      <div className="rounded-lg bg-white border border-slate-200 p-3 shadow-xs">
        <div className="flex items-center gap-2 text-emerald-600 font-semibold text-xs">
          <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
          <span>Tenant Scoped & Secure</span>
        </div>
        <p className="text-[11px] text-slate-500 mt-1">
          Dual-Phase JWT & Fine-Grained RBAC active.
        </p>
      </div>
    </aside>
  );
};
