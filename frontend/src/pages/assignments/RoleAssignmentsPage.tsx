import React, { useState } from 'react';
import {
  useRoleAssignments,
  useRoles,
  useAssignRole,
  useRevokeRoleAssignment,
  UserRoleAssignment,
  RoleScopeType,
} from '@/entities/role';
import { useBranches } from '@/entities/branch';
import { useAuthStore } from '@/features/auth/model/useAuthStore';
import {
  useMyPermissions,
  useHasAnyScopePermission,
  PermissionGuard,
} from '@/entities/permission';
import { parseApiError } from '@/shared/api/errorHandler';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { Badge } from '@/shared/ui/badge';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/shared/ui/card';
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/shared/ui/table';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/shared/ui/dialog';
import {
  Users,
  UserPlus,
  Shield,
  Building2,
  Trash2,
  AlertCircle,
  Loader2,
  CheckCircle2,
  Info,
  Search,
  X,
} from 'lucide-react';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const RoleAssignmentsPage: React.FC = () => {
  const { activeTenant } = useAuthStore();
  const canReadBranches = useHasAnyScopePermission('tenancy.branches.read');

  const [filterMembershipId, setFilterMembershipId] = useState('');
  const [appliedFilter, setAppliedFilter] = useState<string | undefined>(undefined);
  const [filterError, setFilterError] = useState<string | null>(null);

  const { data: assignments = [], isLoading, error, refetch } = useRoleAssignments(appliedFilter);
  const { data: roles = [] } = useRoles();
  const { data: branches = [] } = useBranches({ enabled: canReadBranches });
  const { data: myPermissions } = useMyPermissions();

  const assignRoleMutation = useAssignRole();
  const revokeRoleMutation = useRevokeRoleAssignment();

  // Assign Modal State
  const [open, setOpen] = useState(false);
  const [selectedRoleId, setSelectedRoleId] = useState('');
  const [membershipId, setMembershipId] = useState('');
  const [scopeType, setScopeType] = useState<RoleScopeType>('TENANT');
  const [selectedBranchId, setSelectedBranchId] = useState('');
  const [assignError, setAssignError] = useState<string | null>(null);

  // Revoke Confirm State
  const [revokeAssignment, setRevokeAssignment] = useState<UserRoleAssignment | null>(null);
  const [revokeError, setRevokeError] = useState<string | null>(null);

  const activeRoles = roles.filter((r) => r.isActive);
  const activeBranches = branches.filter((b) => b.isActive);
  const selectedRole = activeRoles.find((r) => r.id === selectedRoleId);
  const isOwnerRoleSelected = selectedRole?.code === 'OWNER';

  const actorTenantPermissions = myPermissions?.tenantPermissions ?? [];
  const actorMayBeOwner = actorTenantPermissions.includes('system.settings.manage');
  const missingRolePermissions = selectedRole
    ? (selectedRole.permissions ?? []).filter((p) => !actorTenantPermissions.includes(p))
    : [];

  const resetAssignForm = () => {
    setSelectedRoleId('');
    setMembershipId('');
    setScopeType('TENANT');
    setSelectedBranchId('');
    setAssignError(null);
  };

  const handleAssignDialogChange = (nextOpen: boolean) => {
    setOpen(nextOpen);
    if (!nextOpen) {
      resetAssignForm();
    } else {
      setAssignError(null);
    }
  };

  const handleRoleChange = (roleId: string) => {
    setSelectedRoleId(roleId);
    const role = activeRoles.find((r) => r.id === roleId);
    if (role?.code === 'OWNER') {
      // OWNER role can only be assigned at store-wide TENANT scope
      setScopeType('TENANT');
      setSelectedBranchId('');
    }
  };

  const handleScopeChange = (nextScope: RoleScopeType) => {
    setScopeType(nextScope);
    if (nextScope === 'TENANT') {
      setSelectedBranchId('');
    }
  };

  const handleApplyFilter = (e: React.FormEvent) => {
    e.preventDefault();
    setFilterError(null);
    const trimmed = filterMembershipId.trim();
    if (!trimmed) {
      setAppliedFilter(undefined);
      return;
    }
    if (!UUID_REGEX.test(trimmed)) {
      setFilterError('Please enter a valid Membership UUID to filter.');
      return;
    }
    setAppliedFilter(trimmed);
  };

  const handleClearFilter = () => {
    setFilterMembershipId('');
    setAppliedFilter(undefined);
    setFilterError(null);
  };

  const handleAssign = async (e: React.FormEvent) => {
    e.preventDefault();
    setAssignError(null);

    const trimmedMembershipId = membershipId.trim();

    if (!trimmedMembershipId) {
      setAssignError('Target Membership ID is required.');
      return;
    }
    if (!UUID_REGEX.test(trimmedMembershipId)) {
      setAssignError('Target Membership ID must be a valid UUID.');
      return;
    }
    if (!selectedRoleId) {
      setAssignError('Please select a role.');
      return;
    }
    if (scopeType === 'BRANCH' && !selectedBranchId) {
      setAssignError('Please select a branch for BRANCH-scoped assignment.');
      return;
    }

    try {
      await assignRoleMutation.mutateAsync({
        membershipId: trimmedMembershipId,
        roleId: selectedRoleId,
        scopeType,
        branchId: scopeType === 'BRANCH' ? selectedBranchId : null,
      });

      resetAssignForm();
      setOpen(false);
    } catch (err: unknown) {
      setAssignError(
        parseApiError(err, 'Failed to assign role. Verify member and permission privileges.').message
      );
    }
  };

  const handleRevoke = async () => {
    if (!revokeAssignment) return;
    setRevokeError(null);

    try {
      await revokeRoleMutation.mutateAsync(revokeAssignment.id);
      setRevokeAssignment(null);
    } catch (err: unknown) {
      setRevokeError(
        parseApiError(err, 'Failed to revoke role assignment.').message
      );
    }
  };

  const getBranchName = (branchId?: string | null) => {
    if (!branchId) return null;
    const branch = branches.find((b) => b.id === branchId);
    return branch ? `${branch.name} (${branch.code})` : branchId;
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <Users className="h-6 w-6 text-slate-900" />
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Role Assignments</h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Assign store roles to members with TENANT or BRANCH scope. Last Active Owner protected.
          </p>
        </div>

        <PermissionGuard permission="identity.roles.manage">
          <Dialog open={open} onOpenChange={handleAssignDialogChange}>
            <DialogTrigger asChild>
              <Button size="sm" className="gap-1.5 bg-slate-900 hover:bg-slate-800">
                <UserPlus className="h-4 w-4" />
                <span>Assign Role</span>
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Assign Role to Member</DialogTitle>
                <DialogDescription>
                  Grant a role with store-wide or branch-specific privileges. Self-assignment is blocked by backend policy.
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleAssign} className="space-y-4 py-2">
                {assignError && (
                  <div role="alert" className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                    {assignError}
                  </div>
                )}

                <div className="space-y-1.5">
                  <Label htmlFor="target-membership">Target Membership ID (UUID) *</Label>
                  <Input
                    id="target-membership"
                    placeholder="e.g. 550e8400-e29b-41d4-a716-446655440000"
                    value={membershipId}
                    onChange={(e) => setMembershipId(e.target.value)}
                    required
                  />
                  <span className="text-[11px] text-slate-400">
                    Active member&apos;s membership UUID in this tenant (your own membership cannot be self-elevated).
                  </span>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="role-select">Select Role *</Label>
                  <select
                    id="role-select"
                    className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-950"
                    value={selectedRoleId}
                    onChange={(e) => handleRoleChange(e.target.value)}
                    required
                  >
                    <option value="">-- Choose a Role --</option>
                    {activeRoles.map((r) => {
                      const isOwnerOption = r.code === 'OWNER';
                      const disableOwner = isOwnerOption && myPermissions && !actorMayBeOwner;
                      return (
                        <option key={r.id} value={r.id} disabled={disableOwner}>
                          {r.name} ({r.code}) {r.isSystem ? '[SYSTEM]' : ''}
                          {disableOwner ? ' — OWNER only' : ''}
                        </option>
                      );
                    })}
                  </select>
                </div>

                {selectedRole && myPermissions && !actorMayBeOwner && missingRolePermissions.length > 0 && (
                  <div className="rounded-md bg-amber-50 p-2.5 text-xs text-amber-800 border border-amber-200">
                    Grant-What-You-Hold Notice: You do not actively hold{' '}
                    <span className="font-mono">{missingRolePermissions.join(', ')}</span>. Backend will reject assigning this role.
                  </div>
                )}

                <div className="space-y-1.5">
                  <Label>Scope Type *</Label>
                  <div className="grid grid-cols-2 gap-3">
                    <label
                      className={`flex items-center gap-2 p-3 rounded-lg border text-xs cursor-pointer ${
                        scopeType === 'TENANT'
                          ? 'border-slate-900 bg-slate-50 font-semibold'
                          : 'border-slate-200 hover:bg-slate-50'
                      }`}
                    >
                      <input
                        type="radio"
                        name="scopeType"
                        value="TENANT"
                        checked={scopeType === 'TENANT'}
                        onChange={() => handleScopeChange('TENANT')}
                      />
                      <div>
                        <div>TENANT Scope</div>
                        <div className="text-[10px] text-slate-500 font-normal">
                          Full store access
                        </div>
                      </div>
                    </label>

                    <label
                      className={`flex items-center gap-2 p-3 rounded-lg border text-xs ${
                        isOwnerRoleSelected
                          ? 'border-slate-200 bg-slate-100 opacity-60 cursor-not-allowed'
                          : scopeType === 'BRANCH'
                          ? 'border-slate-900 bg-slate-50 font-semibold cursor-pointer'
                          : 'border-slate-200 hover:bg-slate-50 cursor-pointer'
                      }`}
                    >
                      <input
                        type="radio"
                        name="scopeType"
                        value="BRANCH"
                        disabled={isOwnerRoleSelected}
                        checked={scopeType === 'BRANCH'}
                        onChange={() => handleScopeChange('BRANCH')}
                      />
                      <div>
                        <div>BRANCH Scope</div>
                        <div className="text-[10px] text-slate-500 font-normal">
                          {isOwnerRoleSelected ? 'Not valid for OWNER' : 'Specific location only'}
                        </div>
                      </div>
                    </label>
                  </div>
                </div>

                {scopeType === 'BRANCH' && (
                  <div className="space-y-1.5">
                    <Label htmlFor="branch-select">Select Branch *</Label>
                    <select
                      id="branch-select"
                      className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-950"
                      value={selectedBranchId}
                      onChange={(e) => setSelectedBranchId(e.target.value)}
                      required
                    >
                      <option value="">-- Choose a Branch --</option>
                      {activeBranches.map((b) => (
                        <option key={b.id} value={b.id}>
                          {b.name} ({b.code})
                        </option>
                      ))}
                    </select>
                    {activeBranches.length === 0 && (
                      <p className="text-[11px] text-amber-600">
                        No active branches available in this store.
                      </p>
                    )}
                  </div>
                )}

                <DialogFooter className="pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => handleAssignDialogChange(false)}
                    disabled={assignRoleMutation.isPending}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    className="bg-slate-900 hover:bg-slate-800"
                    disabled={assignRoleMutation.isPending}
                  >
                    {assignRoleMutation.isPending && (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    )}
                    Assign Role
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        </PermissionGuard>
      </div>

      {/* Backend Member Directory Limitation Notice */}
      <div className="rounded-lg border border-slate-200 bg-white p-3 text-xs text-slate-600 flex items-start gap-2.5">
        <Info className="h-4 w-4 text-slate-400 shrink-0 mt-0.5" />
        <span>
          Assignments reference tenant members by <code className="font-mono text-slate-800">membershipId</code>. Member directory search (displaying employee name/email) is not yet exposed by the backend API in Milestone 04.
        </span>
      </div>

      <Card>
        <CardHeader className="pb-3 space-y-3">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <CardTitle className="text-base">Active Assignments</CardTitle>
              <CardDescription className="text-xs">
                Assigned roles in <strong className="text-slate-900">{activeTenant?.tenantName}</strong>.
              </CardDescription>
            </div>

            <form onSubmit={handleApplyFilter} className="flex items-center gap-2">
              <div className="relative">
                <Input
                  aria-label="Filter by Membership ID"
                  placeholder="Filter by Membership UUID..."
                  value={filterMembershipId}
                  onChange={(e) => setFilterMembershipId(e.target.value)}
                  className="h-8 w-56 sm:w-64 text-xs font-mono pr-7"
                />
                {(filterMembershipId || appliedFilter) && (
                  <button
                    type="button"
                    aria-label="Clear membership filter"
                    onClick={handleClearFilter}
                    className="absolute right-2 top-2 text-slate-400 hover:text-slate-600"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>
              <Button type="submit" variant="outline" size="sm" className="h-8 text-xs gap-1">
                <Search className="h-3.5 w-3.5" />
                <span>Filter</span>
              </Button>
            </form>
          </div>
          {filterError && (
            <div role="alert" className="text-xs text-red-600">
              {filterError}
            </div>
          )}
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex h-32 items-center justify-center text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin mr-2" />
              <span>Loading assignments...</span>
            </div>
          ) : error ? (
            <div role="alert" className="p-4 text-center text-sm text-red-600 bg-red-50 rounded-lg space-y-2">
              <div>{parseApiError(error, 'Failed to load role assignments.').message}</div>
              <Button variant="outline" size="sm" onClick={() => void refetch()}>
                Retry
              </Button>
            </div>
          ) : assignments.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-center text-slate-500">
              <Users className="h-10 w-10 text-slate-300 mb-2" />
              <p className="font-medium text-slate-700">No role assignments found</p>
              <p className="text-xs text-slate-400 mt-1 max-w-sm">
                Each team member must be assigned at least one role to interact with POS or store management.
              </p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Role</TableHead>
                  <TableHead>Membership ID</TableHead>
                  <TableHead>Scope</TableHead>
                  <TableHead>Location / Branch</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Assigned Date</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {assignments.map((assignment) => (
                  <TableRow key={assignment.id}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Shield className="h-4 w-4 text-slate-400" />
                        <div>
                          <div className="font-semibold text-slate-900">{assignment.roleName}</div>
                          <div className="text-[11px] font-mono text-slate-500">{assignment.roleCode}</div>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-slate-700">
                      {assignment.membershipId}
                    </TableCell>
                    <TableCell>
                      {assignment.scopeType === 'TENANT' ? (
                        <Badge variant="secondary" className="gap-1 font-mono text-[10px] bg-slate-100 text-slate-700">
                          TENANT
                        </Badge>
                      ) : (
                        <Badge variant="outline" className="gap-1 font-mono text-[10px] text-blue-700 border-blue-200 bg-blue-50">
                          BRANCH
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-xs text-slate-600">
                      {assignment.scopeType === 'BRANCH' ? (
                        <div className="flex items-center gap-1.5 font-medium text-slate-800">
                          <Building2 className="h-3.5 w-3.5 text-slate-400" />
                          <span>{getBranchName(assignment.branchId)}</span>
                        </div>
                      ) : (
                        <span className="text-slate-400 italic">Entire Store</span>
                      )}
                    </TableCell>
                    <TableCell>
                      {assignment.isActive ? (
                        <Badge variant="success" className="gap-1 font-normal text-[11px]">
                          <CheckCircle2 className="h-3 w-3" />
                          Active
                        </Badge>
                      ) : (
                        <Badge variant="destructive" className="font-normal text-[11px]">
                          Revoked
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-xs text-slate-500 font-mono">
                      {new Date(assignment.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell className="text-right">
                      {assignment.isActive && (
                        <PermissionGuard permission="identity.roles.manage">
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-8 text-xs text-red-600 hover:text-red-700 hover:bg-red-50 border-red-100"
                            onClick={() => {
                              setRevokeError(null);
                              setRevokeAssignment(assignment);
                            }}
                          >
                            <Trash2 className="h-3.5 w-3.5 mr-1" />
                            Revoke
                          </Button>
                        </PermissionGuard>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Revoke Confirm Dialog */}
      {revokeAssignment && (
        <Dialog
          open={!!revokeAssignment}
          onOpenChange={(open) => !open && setRevokeAssignment(null)}
        >
          <DialogContent>
            <DialogHeader>
              <div className="flex items-center gap-2 text-red-600">
                <AlertCircle className="h-5 w-5" />
                <DialogTitle>Revoke Role Assignment</DialogTitle>
              </div>
              <DialogDescription>
                Revoke role <strong className="text-slate-900">{revokeAssignment.roleName}</strong> (
                {revokeAssignment.roleCode}) for membership{' '}
                <span className="font-mono text-xs">{revokeAssignment.membershipId}</span>?
              </DialogDescription>
            </DialogHeader>

            <div className="rounded-md bg-amber-50 p-3 text-xs text-amber-800 border border-amber-200 space-y-1">
              <div className="font-semibold">Last Active Owner Invariant:</div>
              <div>
                The system prevents revoking the last active OWNER of the store to guarantee that
                every tenant remains administrable.
              </div>
            </div>

            {revokeError && (
              <div role="alert" className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                {revokeError}
              </div>
            )}

            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setRevokeAssignment(null)}
                disabled={revokeRoleMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={handleRevoke}
                disabled={revokeRoleMutation.isPending}
              >
                {revokeRoleMutation.isPending && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                Confirm Revocation
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
};
