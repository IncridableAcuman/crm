import { Permission } from './types';

/**
 * Readable UI labels for the 33 canonical backend permissions.
 * Backend catalog (`GET /api/v1/permissions`) remains the single source of truth;
 * unknown future codes fall back cleanly to formatted resource/action labels.
 */
const PERMISSION_LABELS: Record<string, string> = {
  'analytics.dashboard.read': 'View Dashboard',
  'analytics.reports.read': 'View Analytics Reports',
  'catalog.products.read': 'View Products',
  'catalog.products.create': 'Create Products',
  'catalog.products.update': 'Update Products',
  'catalog.products.delete': 'Delete Products',
  'catalog.categories.read': 'View Categories',
  'catalog.categories.manage': 'Manage Categories',
  'inventory.stock.read': 'View Stock Levels',
  'inventory.adjustments.create': 'Create Stock Adjustments',
  'inventory.transfers.create': 'Create Stock Transfers',
  'sales.orders.read': 'View Sales Orders',
  'sales.orders.create': 'Create Sales Orders & POS',
  'sales.refunds.create': 'Process Sales Refunds',
  'purchases.orders.read': 'View Purchase Orders',
  'purchases.orders.create': 'Create Purchase Orders',
  'purchases.receipts.create': 'Receive Purchase Goods',
  'parties.customers.read': 'View Customers',
  'parties.customers.manage': 'Manage Customers',
  'parties.suppliers.read': 'View Suppliers',
  'parties.suppliers.manage': 'Manage Suppliers',
  'finance.accounts.read': 'View Financial Accounts',
  'finance.expenses.read': 'View Expenses',
  'finance.expenses.create': 'Record Expenses',
  'tenancy.branches.read': 'View Store Branches',
  'tenancy.branches.manage': 'Manage Store Branches',
  'identity.users.read': 'View Team Members',
  'identity.users.manage': 'Manage Team Members',
  'identity.roles.read': 'View Roles & Permissions',
  'identity.roles.manage': 'Manage Roles & Assignments',
  'system.audit_logs.read': 'View Security Audit Logs',
  'system.settings.read': 'View Store Settings',
  'system.settings.manage': 'Manage Store Settings',
};

const MODULE_LABELS: Record<string, string> = {
  analytics: 'Analytics',
  catalog: 'Catalog',
  inventory: 'Inventory',
  sales: 'Sales & POS',
  purchases: 'Purchases',
  parties: 'Customers & Suppliers',
  finance: 'Finance',
  tenancy: 'Store & Branches',
  identity: 'Identity & RBAC',
  system: 'System & Audit',
};

function toTitleCase(segment: string): string {
  return segment
    .split(/[_-]+/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
}

export function getPermissionLabel(permission: Pick<Permission, 'code'> & Partial<Pick<Permission, 'resource' | 'action'>>): string {
  const mapped = PERMISSION_LABELS[permission.code];
  if (mapped) {
    return mapped;
  }

  if (permission.action && permission.resource) {
    return `${toTitleCase(permission.action)} ${toTitleCase(permission.resource)}`;
  }

  const parts = permission.code.split('.');
  if (parts.length === 3) {
    return `${toTitleCase(parts[2] ?? '')} ${toTitleCase(parts[1] ?? '')}`.trim();
  }

  return permission.code;
}

export function getModuleLabel(moduleCode: string): string {
  return MODULE_LABELS[moduleCode] ?? toTitleCase(moduleCode);
}
