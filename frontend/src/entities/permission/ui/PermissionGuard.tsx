import React from 'react';
import { useHasPermission } from '../model/permissionQueries';

export interface PermissionGuardProps {
  permission: string;
  branchId?: string;
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * Renders children only if current user has the required permission.
 * Otherwise renders fallback (defaults to null).
 */
export const PermissionGuard: React.FC<PermissionGuardProps> = ({
  permission,
  branchId,
  fallback = null,
  children,
}) => {
  const hasPermission = useHasPermission(permission, branchId);

  if (!hasPermission) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
};
