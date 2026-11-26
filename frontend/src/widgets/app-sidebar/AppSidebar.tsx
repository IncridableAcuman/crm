import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  GitBranch,
  ShieldCheck,
  Users,
  ShoppingCart,
  Package,
  FolderTree,
  Boxes,
} from 'lucide-react';
import { cn } from '@/shared/lib/utils';
import { Badge } from '@/shared/ui/badge';
import { useHasPermission, useHasAnyScopePermission } from '@/entities/permission';

interface NavItem {
  title: string;
  to: string;
  icon: React.ElementType;
  visible?: boolean;
  badge?: string;
}

interface AppSidebarProps {
  mobileOpen?: boolean;
  onClose?: () => void;
}

export const AppSidebar: React.FC<AppSidebarProps> = ({
  mobileOpen = false,
  onClose,
}) => {
  const canReadBranches = useHasAnyScopePermission('tenancy.branches.read');
  const canReadRoles = useHasPermission('identity.roles.read');
  const canReadProducts = useHasAnyScopePermission('catalog.products.read');
  const canReadCategories = useHasAnyScopePermission('catalog.categories.read');

  const mainNavItems: NavItem[] = [
    {
      title: 'Dashboard',
      to: '/',
      icon: LayoutDashboard,
      visible: true,
    },
    {
      title: 'Product Catalog',
      to: '/catalog/products',
      icon: Package,
      visible: canReadProducts,
    },
    {
      title: 'Categories & Units',
      to: '/catalog/categories',
      icon: FolderTree,
      visible: canReadCategories,
    },
    {
      title: 'Store Branches',
      to: '/branches',
      icon: GitBranch,
      visible: canReadBranches,
    },
    {
      title: 'Roles & Permissions',
      to: '/roles',
      icon: ShieldCheck,
      visible: canReadRoles,
    },
    {
      title: 'Role Assignments',
      to: '/assignments',
      icon: Users,
      visible: canReadRoles,
    },
  ];

  const comingSoonItems: NavItem[] = [
    {
      title: 'Stock & Inventory',
      to: '/inventory',
      icon: Boxes,
      badge: 'M06',
    },
    {
      title: 'Point of Sale (POS)',
      to: '/pos',
      icon: ShoppingCart,
      badge: 'M07',
    },
  ];

  const sidebarContent = (
    <div className="flex h-full flex-col justify-between p-4">
      <div className="space-y-6">
        <div>
          <div className="px-3 mb-2 text-[11px] font-semibold tracking-wider text-slate-400 uppercase">
            Store Management
          </div>
          <nav className="space-y-1" aria-label="Main navigation">
            {mainNavItems.map((item) => {
              if (!item.visible) {
                return null;
              }

              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  onClick={onClose}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all',
                      isActive
                        ? 'bg-slate-900 text-white shadow-sm'
                        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                    )
                  }
                >
                  <Icon className="h-4 w-4 shrink-0" />
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
                  aria-disabled="true"
                  className="flex items-center justify-between rounded-lg px-3 py-2 text-sm text-slate-400 cursor-not-allowed select-none"
                >
                  <div className="flex items-center gap-3">
                    <Icon className="h-4 w-4 text-slate-300 shrink-0" />
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
      <div className="rounded-lg bg-white border border-slate-200 p-3 shadow-xs mt-6">
        <div className="flex items-center gap-2 text-emerald-600 font-semibold text-xs">
          <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
          <span>Tenant Scoped & Secure</span>
        </div>
        <p className="text-[11px] text-slate-500 mt-1">
          Dual-Phase JWT & Fine-Grained RBAC active.
        </p>
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop / Tablet Sidebar */}
      <aside className="hidden md:block w-64 shrink-0 border-r border-slate-200 bg-slate-50/50 min-h-[calc(100vh-4rem)] overflow-y-auto">
        {sidebarContent}
      </aside>

      {/* Mobile Drawer Sidebar */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 flex md:hidden">
          <div
            className="fixed inset-0 bg-black/40 backdrop-blur-xs"
            onClick={onClose}
            aria-hidden="true"
          />
          <aside className="relative z-10 w-72 max-w-[85vw] overflow-y-auto bg-slate-50 border-r border-slate-200 min-h-screen shadow-xl">
            {sidebarContent}
          </aside>
        </div>
      )}
    </>
  );
};
