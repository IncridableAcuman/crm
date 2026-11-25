import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/shared/ui/card';
import { Button } from '@/shared/ui/button';
import { Badge } from '@/shared/ui/badge';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import { useCurrentUser } from '@/features/auth/model/authQueries';
import { useBranches } from '@/entities/branch';
import { useRoles, useRoleAssignments } from '@/entities/role';
import {
  useMyPermissions,
  useHasPermission,
  useHasAnyScopePermission,
  PermissionGuard,
  getPermissionLabel,
} from '@/entities/permission';
import {
  GitBranch,
  ShieldCheck,
  Users,
  Store,
  ArrowRight,
  Shield,
  Layers,
  Key,
  CheckCircle2,
  Lock,
  Building2,
  RefreshCw,
} from 'lucide-react';

export const HomePage: React.FC = () => {
  const navigate = useNavigate();
  const { activeTenant } = useAuthStore();
  const { data: authData } = useCurrentUser();
  const user = authData?.user;

  const {
    data: permissions,
    isLoading: permissionsLoading,
    isError: permissionsError,
    refetch: refetchPermissions,
  } = useMyPermissions();

  const canReadBranches = useHasAnyScopePermission('tenancy.branches.read');
  const canReadRoles = useHasPermission('identity.roles.read');

  const {
    data: branches = [],
    isLoading: branchesLoading,
    isError: branchesError,
  } = useBranches({ enabled: canReadBranches });

  const {
    data: roles = [],
    isLoading: rolesLoading,
    isError: rolesError,
  } = useRoles({ enabled: canReadRoles });

  const {
    data: assignments = [],
    isLoading: assignmentsLoading,
    isError: assignmentsError,
  } = useRoleAssignments(undefined, { enabled: canReadRoles });

  const activeBranchesCount = branches.filter((b) => b.isActive).length;
  const activeAssignmentsCount = assignments.filter((a) => a.isActive).length;

  const tenantPerms = permissions?.tenantPermissions ?? [];
  const branchPermEntries = Object.entries(permissions?.branchPermissions ?? {});
  const totalUniqueEffectivePermissions = new Set<string>([
    ...tenantPerms,
    ...branchPermEntries.flatMap(([, perms]) => perms),
  ]).size;

  const getBranchLabel = (branchId: string) => {
    const branch = branches.find((b) => b.id === branchId);
    return branch ? `${branch.name} (${branch.code})` : branchId;
  };

  const renderMetricValue = (
    allowed: boolean,
    loading: boolean,
    error: boolean,
    value: number
  ) => {
    if (permissionsLoading || (allowed && loading)) {
      return <span className="text-slate-400">...</span>;
    }
    if (permissionsError || (allowed && error)) {
      return <span className="text-sm font-medium text-red-600">Unavailable</span>;
    }
    if (!allowed) {
      return <span className="text-sm font-medium text-slate-400">Restricted</span>;
    }
    return value;
  };

  return (
    <div className="space-y-8">
      {/* Welcome Banner */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-4 border-b border-slate-200 gap-4">
        <div>
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              Welcome back{user?.firstName ? `, ${user.firstName}` : ''}
            </h1>
            <Badge variant="outline" className="font-mono text-xs">
              {activeTenant?.tenantCode}
            </Badge>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Store overview, branches, roles, and real-time RBAC privilege monitoring.
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {canReadBranches && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/branches')}
              className="text-xs gap-1.5"
            >
              <GitBranch className="h-3.5 w-3.5" />
              <span>Manage Branches</span>
            </Button>
          )}

          {canReadRoles && (
            <Button
              size="sm"
              onClick={() => navigate('/roles')}
              className="text-xs gap-1.5 bg-slate-900 hover:bg-slate-800"
            >
              <ShieldCheck className="h-3.5 w-3.5" />
              <span>Roles & Permissions</span>
            </Button>
          )}
        </div>
      </div>

      {/* Live Operational Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="hover:border-slate-300 transition-colors">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-slate-600">Active Branches</CardTitle>
            <GitBranch className="h-4 w-4 text-slate-400" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-slate-900">
              {renderMetricValue(canReadBranches, branchesLoading, branchesError, activeBranchesCount)}
            </div>
            {canReadBranches ? (
              <button
                type="button"
                onClick={() => navigate('/branches')}
                className="w-full text-[11px] text-slate-500 mt-1 flex items-center justify-between hover:text-slate-900"
              >
                <span>Configured store locations</span>
                <ArrowRight className="h-3 w-3 text-slate-400" />
              </button>
            ) : (
              <p className="text-[11px] text-slate-400 mt-1">Requires tenancy.branches.read</p>
            )}
          </CardContent>
        </Card>

        <Card className="hover:border-slate-300 transition-colors">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-slate-600">Store Roles</CardTitle>
            <ShieldCheck className="h-4 w-4 text-slate-400" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-slate-900">
              {renderMetricValue(canReadRoles, rolesLoading, rolesError, roles.length)}
            </div>
            {canReadRoles ? (
              <button
                type="button"
                onClick={() => navigate('/roles')}
                className="w-full text-[11px] text-slate-500 mt-1 flex items-center justify-between hover:text-slate-900"
              >
                <span>System & custom roles</span>
                <ArrowRight className="h-3 w-3 text-slate-400" />
              </button>
            ) : (
              <p className="text-[11px] text-slate-400 mt-1">Requires identity.roles.read</p>
            )}
          </CardContent>
        </Card>

        <Card className="hover:border-slate-300 transition-colors">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-slate-600">Role Assignments</CardTitle>
            <Users className="h-4 w-4 text-slate-400" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-slate-900">
              {renderMetricValue(canReadRoles, assignmentsLoading, assignmentsError, activeAssignmentsCount)}
            </div>
            {canReadRoles ? (
              <button
                type="button"
                onClick={() => navigate('/assignments')}
                className="w-full text-[11px] text-slate-500 mt-1 flex items-center justify-between hover:text-slate-900"
              >
                <span>Active team member assignments</span>
                <ArrowRight className="h-3 w-3 text-slate-400" />
              </button>
            ) : (
              <p className="text-[11px] text-slate-400 mt-1">Requires identity.roles.read</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-slate-600">My Permissions</CardTitle>
            <Key className="h-4 w-4 text-emerald-600" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-emerald-600">
              {permissionsLoading ? (
                <span className="text-slate-400">...</span>
              ) : permissionsError ? (
                <span className="text-sm font-medium text-red-600">Error</span>
              ) : (
                totalUniqueEffectivePermissions
              )}
            </div>
            <p className="text-[11px] text-slate-500 mt-1">
              {branchPermEntries.length > 0 && tenantPerms.length === 0
                ? `Branch-scoped across ${branchPermEntries.length} branch(es)`
                : 'Active effective capabilities'}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Module Navigation & Details */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Module Status Card */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Store className="h-5 w-5 text-slate-900" />
              <CardTitle className="text-base">Store Architecture & Modules</CardTitle>
            </div>
            <CardDescription className="text-xs">
              Backend and frontend status for active and upcoming system capabilities.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex items-center justify-between p-3 rounded-lg border border-slate-100 bg-slate-50/50">
              <div className="flex items-center gap-3">
                <ShieldCheck className="h-4 w-4 text-emerald-600" />
                <div>
                  <div className="text-xs font-semibold text-slate-900">Identity & RBAC Core</div>
                  <div className="text-[11px] text-slate-500">Authentication, roles, permissions, assignments</div>
                </div>
              </div>
              <Badge variant="success" className="text-[10px]">Ready & Active</Badge>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg border border-slate-100 bg-slate-50/50">
              <div className="flex items-center gap-3">
                <GitBranch className="h-4 w-4 text-emerald-600" />
                <div>
                  <div className="text-xs font-semibold text-slate-900">Branch Management</div>
                  <div className="text-[11px] text-slate-500">Store locations, contact details, and primary branch</div>
                </div>
              </div>
              <Badge variant="success" className="text-[10px]">Ready & Active</Badge>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg border border-slate-100 bg-slate-50/50">
              <div className="flex items-center gap-3">
                <Layers className="h-4 w-4 text-slate-400" />
                <div>
                  <div className="text-xs font-semibold text-slate-700">Catalog & Products</div>
                  <div className="text-[11px] text-slate-400">Categories, brands, units of measure, variants</div>
                </div>
              </div>
              <Badge variant="secondary" className="text-[10px] text-slate-500">Milestone 05</Badge>
            </div>

            <div className="flex items-center justify-between p-3 rounded-lg border border-slate-100 bg-slate-50/50">
              <div className="flex items-center gap-3">
                <Lock className="h-4 w-4 text-slate-400" />
                <div>
                  <div className="text-xs font-semibold text-slate-700">POS & Sales Engine</div>
                  <div className="text-[11px] text-slate-400">High-speed touch/scanner checkout & receipt print</div>
                </div>
              </div>
              <Badge variant="secondary" className="text-[10px] text-slate-500">Milestone 06</Badge>
            </div>
          </CardContent>
        </Card>

        {/* Current Active Permissions Card */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Shield className="h-5 w-5 text-slate-900" />
                <CardTitle className="text-base">Your Active Privileges</CardTitle>
              </div>
              <span className="text-xs font-mono text-slate-500">
                {activeTenant?.tenantCode}
              </span>
            </div>
            <CardDescription className="text-xs">
              Live permissions resolved by `PermissionAuthorizationService`.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {permissionsLoading ? (
              <div className="py-6 text-center text-xs text-slate-400">
                Loading active permissions...
              </div>
            ) : permissionsError ? (
              <div role="alert" className="rounded-md bg-red-50 p-4 text-center text-xs text-red-700 border border-red-200 space-y-2">
                <div>Failed to load your effective permissions.</div>
                <Button variant="outline" size="sm" onClick={() => void refetchPermissions()} className="gap-1.5">
                  <RefreshCw className="h-3.5 w-3.5" />
                  Retry
                </Button>
              </div>
            ) : tenantPerms.length > 0 || branchPermEntries.length > 0 ? (
              <div className="space-y-3 max-h-64 overflow-y-auto p-1">
                {tenantPerms.length > 0 && (
                  <div className="space-y-1.5">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                      Store-Wide (TENANT Scope)
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      {tenantPerms.map((code) => (
                        <Badge
                          key={code}
                          variant="outline"
                          title={getPermissionLabel({ code })}
                          className="font-mono text-[10px] text-slate-700 bg-slate-50 border-slate-200"
                        >
                          {code}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}

                {branchPermEntries.map(([branchId, perms]) => (
                  <div key={branchId} className="space-y-1.5">
                    <div className="flex items-center gap-1 text-[11px] font-semibold uppercase tracking-wider text-blue-600">
                      <Building2 className="h-3 w-3" />
                      <span>Branch Scope: {getBranchLabel(branchId)}</span>
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      {perms.map((code) => (
                        <Badge
                          key={`${branchId}-${code}`}
                          variant="outline"
                          title={getPermissionLabel({ code })}
                          className="font-mono text-[10px] text-blue-700 bg-blue-50 border-blue-200"
                        >
                          {code}
                        </Badge>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-6 text-slate-400 text-xs">
                No active permissions assigned. Contact your store owner.
              </div>
            )}

            <div className="mt-4 pt-4 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
              <div className="flex items-center gap-1.5 text-emerald-600 font-medium">
                <CheckCircle2 className="h-3.5 w-3.5" />
                <span>Fail-closed security verified</span>
              </div>
              <PermissionGuard permission="identity.roles.read">
                <Button
                  variant="link"
                  size="sm"
                  onClick={() => navigate('/roles')}
                  className="text-xs p-0 h-auto text-slate-900"
                >
                  Manage Roles →
                </Button>
              </PermissionGuard>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
