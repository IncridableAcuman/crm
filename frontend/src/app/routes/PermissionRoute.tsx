import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useMyPermissions, useHasPermission, useHasAnyScopePermission } from '@/entities/permission';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { ShieldAlert, Loader2, RefreshCw } from 'lucide-react';

interface PermissionRouteProps {
  permission: string;
  allowBranchScope?: boolean;
}

/**
 * Route-level fail-closed permission guard.
 * Prevents protected pages from mounting or firing unauthorized queries while
 * permissions are loading, when the permissions API fails, or when access is denied.
 */
export const PermissionRoute: React.FC<PermissionRouteProps> = ({
  permission,
  allowBranchScope = false,
}) => {
  const { isLoading, isError, refetch, isFetching } = useMyPermissions();
  const hasTenantPerm = useHasPermission(permission);
  const hasAnyScopePerm = useHasAnyScopePermission(permission);

  const isAuthorized = allowBranchScope ? hasAnyScopePerm : hasTenantPerm;

  if (isLoading) {
    return (
      <div
        className="flex min-h-[320px] flex-col items-center justify-center gap-2 text-slate-500"
        role="status"
        aria-live="polite"
      >
        <Loader2 className="h-7 w-7 animate-spin text-slate-700" />
        <span className="text-sm">Verifying permissions...</span>
      </div>
    );
  }

  if (isError) {
    return (
      <Card className="mx-auto mt-8 max-w-md border-red-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base text-red-700">
            <ShieldAlert className="h-5 w-5" />
            <span>Unable to Verify Permissions</span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4 text-sm text-slate-600">
          <p>
            Access is blocked because your effective permissions could not be loaded from the server.
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => void refetch()}
              disabled={isFetching}
              className="gap-1.5"
            >
              <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? 'animate-spin' : ''}`} />
              <span>Retry</span>
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => window.location.assign('/')}
            >
              Back to Dashboard
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!isAuthorized) {
    return (
      <Card className="mx-auto mt-8 max-w-md border-amber-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base text-slate-900">
            <ShieldAlert className="h-5 w-5 text-amber-600" />
            <span>Access Denied</span>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4 text-sm text-slate-600">
          <p>
            You do not have the required permission (<code className="font-mono text-xs">{permission}</code>) to view this page.
          </p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => window.history.length > 1 ? window.history.back() : undefined}
            asChild
          >
            <a href="/">Return to Dashboard</a>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return <Outlet />;
};

export const UnauthorizedRedirect: React.FC = () => <Navigate to="/" replace />;
