import React, { useState } from 'react';
import {
  useRoles,
  useCreateRole,
  useUpdateRole,
  useUpdateRolePermissions,
  useDeleteRole,
  Role,
} from '@/entities/role';
import {
  usePermissionsCatalog,
  useMyPermissions,
  useHasPermission,
  PermissionGuard,
  getPermissionLabel,
  getModuleLabel,
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
  ShieldCheck,
  Plus,
  Key,
  Trash2,
  Lock,
  Loader2,
  CheckCircle2,
  AlertTriangle,
  Info,
  Pencil,
} from 'lucide-react';

const ROLE_CODE_REGEX = /^[A-Z0-9_]{3,50}$/;

export const RolesPage: React.FC = () => {
  const { data: roles = [], isLoading, error, refetch } = useRoles();
  const {
    data: catalog = [],
    isLoading: catalogLoading,
    error: catalogError,
  } = usePermissionsCatalog();
  const { data: myPermissions } = useMyPermissions();
  const canManageRoles = useHasPermission('identity.roles.manage');

  const createRoleMutation = useCreateRole();
  const updateRoleMutation = useUpdateRole();
  const updatePermissionsMutation = useUpdateRolePermissions();
  const deleteRoleMutation = useDeleteRole();

  // Create Role State
  const [createOpen, setCreateOpen] = useState(false);
  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
  const [createError, setCreateError] = useState<string | null>(null);

  // Edit Custom Role Details State
  const [editDetailsRole, setEditDetailsRole] = useState<Role | null>(null);
  const [editDetailsName, setEditDetailsName] = useState('');
  const [editDetailsDescription, setEditDetailsDescription] = useState('');
  const [editDetailsError, setEditDetailsError] = useState<string | null>(null);

  // Edit / View Permissions State
  const [editRole, setEditRole] = useState<Role | null>(null);
  const [editingPermissions, setEditingPermissions] = useState<string[]>([]);
  const [editError, setEditError] = useState<string | null>(null);

  // Delete Confirm State
  const [deleteConfirmRole, setDeleteConfirmRole] = useState<Role | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Group backend permission catalog by module (single source of truth)
  const permissionsByModule = catalog.reduce<Record<string, typeof catalog>>((acc, perm) => {
    const mod = perm.module || 'other';
    if (!acc[mod]) acc[mod] = [];
    acc[mod].push(perm);
    return acc;
  }, {});

  const totalCatalogCount = catalog.length;

  const handleCreateOpenChange = (nextOpen: boolean) => {
    setCreateOpen(nextOpen);
    if (!nextOpen) {
      setNewCode('');
      setNewName('');
      setNewDescription('');
      setSelectedPermissions([]);
      setCreateError(null);
    } else {
      setCreateError(null);
    }
  };

  const handleCreateRole = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);

    const normalizedCode = newCode.trim().toUpperCase();
    const trimmedName = newName.trim();

    if (!normalizedCode || !trimmedName) {
      setCreateError('Role code and name are required.');
      return;
    }

    if (!ROLE_CODE_REGEX.test(normalizedCode)) {
      setCreateError('Role code must consist of 3 to 50 uppercase alphanumeric characters or underscores.');
      return;
    }

    if (trimmedName.length > 100) {
      setCreateError('Role name cannot exceed 100 characters.');
      return;
    }

    try {
      await createRoleMutation.mutateAsync({
        code: normalizedCode,
        name: trimmedName,
        description: newDescription.trim() || null,
        permissions: selectedPermissions,
      });
      handleCreateOpenChange(false);
    } catch (err: unknown) {
      setCreateError(
        parseApiError(err, 'Failed to create role. Ensure you hold all selected permissions.').message
      );
    }
  };

  const handleOpenEditDetails = (role: Role) => {
    setEditDetailsRole(role);
    setEditDetailsName(role.name);
    setEditDetailsDescription(role.description ?? '');
    setEditDetailsError(null);
  };

  const handleSaveRoleDetails = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editDetailsRole) return;
    setEditDetailsError(null);

    const trimmedName = editDetailsName.trim();
    if (!trimmedName) {
      setEditDetailsError('Role name is required.');
      return;
    }
    if (trimmedName.length > 100) {
      setEditDetailsError('Role name cannot exceed 100 characters.');
      return;
    }

    try {
      await updateRoleMutation.mutateAsync({
        roleId: editDetailsRole.id,
        data: {
          name: trimmedName,
          description: editDetailsDescription.trim() || null,
        },
      });
      setEditDetailsRole(null);
    } catch (err: unknown) {
      setEditDetailsError(parseApiError(err, 'Failed to update role details.').message);
    }
  };

  const handleOpenEditPermissions = (role: Role) => {
    setEditRole(role);
    setEditingPermissions(role.permissions || []);
    setEditError(null);
  };

  const handleSavePermissions = async () => {
    if (!editRole || editRole.isSystem || !canManageRoles) return;
    setEditError(null);

    try {
      await updatePermissionsMutation.mutateAsync({
        roleId: editRole.id,
        permissions: editingPermissions,
      });
      setEditRole(null);
    } catch (err: unknown) {
      setEditError(
        parseApiError(err, 'Failed to update permissions (Grant-What-You-Hold violation).').message
      );
    }
  };

  const handleDeleteRole = async () => {
    if (!deleteConfirmRole) return;
    setDeleteError(null);

    try {
      await deleteRoleMutation.mutateAsync(deleteConfirmRole.id);
      setDeleteConfirmRole(null);
    } catch (err: unknown) {
      setDeleteError(parseApiError(err, 'Failed to delete role.').message);
    }
  };

  const togglePermission = (code: string, isCreate: boolean) => {
    if (isCreate) {
      setSelectedPermissions((prev) =>
        prev.includes(code) ? prev.filter((p) => p !== code) : [...prev, code]
      );
    } else {
      setEditingPermissions((prev) =>
        prev.includes(code) ? prev.filter((p) => p !== code) : [...prev, code]
      );
    }
  };

  const actorTenantPermissions = myPermissions?.tenantPermissions ?? [];
  const isEditPermissionsReadOnly = !editRole || editRole.isSystem || !canManageRoles;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <ShieldCheck className="h-6 w-6 text-slate-900" />
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">Roles & Permissions</h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            System and custom roles governing tenant and branch access rights.
          </p>
        </div>

        <PermissionGuard permission="identity.roles.manage">
          <Dialog open={createOpen} onOpenChange={handleCreateOpenChange}>
            <DialogTrigger asChild>
              <Button size="sm" className="gap-1.5 bg-slate-900 hover:bg-slate-800">
                <Plus className="h-4 w-4" />
                <span>Create Custom Role</span>
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle>Create Custom Role</DialogTitle>
                <DialogDescription>
                  Define a new role and grant specific permissions (Grant-What-You-Hold enforced by backend).
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleCreateRole} className="space-y-4 py-2">
                {createError && (
                  <div role="alert" className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                    {createError}
                  </div>
                )}

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="role-code">Role Code *</Label>
                    <Input
                      id="role-code"
                      placeholder="e.g. SUPERVISOR"
                      value={newCode}
                      onChange={(e) => setNewCode(e.target.value.toUpperCase())}
                      required
                    />
                    <span className="text-[11px] text-slate-400">3–50 chars (A–Z, 0–9, _)</span>
                  </div>

                  <div className="space-y-1.5">
                    <Label htmlFor="role-name">Display Name *</Label>
                    <Input
                      id="role-name"
                      placeholder="e.g. Shift Supervisor"
                      value={newName}
                      onChange={(e) => setNewName(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <Label htmlFor="role-description">Description</Label>
                  <Input
                    id="role-description"
                    placeholder="e.g. Handles cashier overrides and shift reconciliation"
                    value={newDescription}
                    onChange={(e) => setNewDescription(e.target.value)}
                  />
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label>Assign Initial Permissions</Label>
                    <span className="text-xs text-slate-500">
                      {selectedPermissions.length}
                      {totalCatalogCount > 0 ? ` / ${totalCatalogCount}` : ''} selected
                    </span>
                  </div>

                  <div className="border border-slate-200 rounded-lg p-3 max-h-60 overflow-y-auto space-y-4 bg-slate-50/50">
                    {catalogLoading ? (
                      <div className="flex items-center justify-center py-6 text-xs text-slate-500">
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Loading permission catalog...
                      </div>
                    ) : catalogError ? (
                      <div role="alert" className="text-xs text-red-600 p-2">
                        {parseApiError(catalogError, 'Failed to load permission catalog.').message}
                      </div>
                    ) : (
                      Object.entries(permissionsByModule).map(([moduleName, perms]) => (
                        <div key={moduleName} className="space-y-2">
                          <div className="text-xs font-semibold uppercase text-slate-500 tracking-wider">
                            {getModuleLabel(moduleName)}
                          </div>
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                            {perms.map((p) => {
                              const isChecked = selectedPermissions.includes(p.code);
                              const actorHasIt = myPermissions
                                ? actorTenantPermissions.includes(p.code)
                                : false;
                              const isDisabled = !myPermissions || (!actorHasIt && !isChecked);

                              return (
                                <label
                                  key={p.id}
                                  className={`flex items-start gap-2 p-2 rounded-md border text-xs transition-colors ${
                                    isChecked
                                      ? 'bg-slate-900 text-white border-slate-900'
                                      : isDisabled
                                      ? 'bg-slate-100 text-slate-400 border-slate-200 cursor-not-allowed opacity-75'
                                      : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-100 cursor-pointer'
                                  }`}
                                >
                                  <input
                                    type="checkbox"
                                    disabled={isDisabled}
                                    className="mt-0.5 rounded border-slate-300"
                                    checked={isChecked}
                                    onChange={() => togglePermission(p.code, true)}
                                  />
                                  <div className="min-w-0">
                                    <div className="font-medium">{getPermissionLabel(p)}</div>
                                    <div
                                      className={`text-[10px] font-mono break-all ${
                                        isChecked ? 'text-slate-300' : 'text-slate-400'
                                      }`}
                                    >
                                      {p.code}
                                    </div>
                                    {p.description && (
                                      <div
                                        className={`text-[10px] mt-0.5 ${
                                          isChecked ? 'text-slate-300' : 'text-slate-500'
                                        }`}
                                      >
                                        {p.description}
                                      </div>
                                    )}
                                    {myPermissions && !actorHasIt && (
                                      <div className="text-[10px] text-amber-600 font-medium mt-0.5">
                                        Requires permission you do not hold
                                      </div>
                                    )}
                                  </div>
                                </label>
                              );
                            })}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>

                <DialogFooter className="pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => handleCreateOpenChange(false)}
                    disabled={createRoleMutation.isPending}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    className="bg-slate-900 hover:bg-slate-800"
                    disabled={createRoleMutation.isPending}
                  >
                    {createRoleMutation.isPending && (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    )}
                    Create Role
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>
        </PermissionGuard>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Store Roles</CardTitle>
          <CardDescription className="text-xs">
            Built-in system roles (immutable) and customizable store roles.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex h-32 items-center justify-center text-slate-400">
              <Loader2 className="h-6 w-6 animate-spin mr-2" />
              <span>Loading roles...</span>
            </div>
          ) : error ? (
            <div role="alert" className="p-4 text-center text-sm text-red-600 bg-red-50 rounded-lg space-y-2">
              <div>{parseApiError(error, 'Failed to load roles.').message}</div>
              <Button variant="outline" size="sm" onClick={() => void refetch()}>
                Retry
              </Button>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Role</TableHead>
                  <TableHead>Code</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Permissions</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {roles.map((role) => {
                  const permCount = role.permissions?.length ?? 0;
                  return (
                    <TableRow key={role.id}>
                      <TableCell className="font-semibold text-slate-900">
                        {role.name}
                      </TableCell>
                      <TableCell className="font-mono text-xs text-slate-700">
                        {role.code}
                      </TableCell>
                      <TableCell className="text-xs text-slate-500 max-w-xs truncate">
                        {role.description || <span className="italic text-slate-400">None</span>}
                      </TableCell>
                      <TableCell>
                        {role.isSystem ? (
                          <Badge variant="secondary" className="gap-1 font-mono text-[10px] bg-slate-100 text-slate-700">
                            <Lock className="h-3 w-3 text-slate-400" />
                            SYSTEM
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="font-mono text-[10px] text-blue-700 border-blue-200 bg-blue-50">
                            CUSTOM
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell className="text-xs text-slate-600 font-mono">
                        {role.code === 'OWNER'
                          ? totalCatalogCount > 0
                            ? `All (${permCount || totalCatalogCount}/${totalCatalogCount})`
                            : `All (${permCount})`
                          : `${permCount} permissions`}
                      </TableCell>
                      <TableCell>
                        {role.isActive ? (
                          <Badge variant="success" className="gap-1 font-normal text-[11px]">
                            <CheckCircle2 className="h-3 w-3" />
                            Active
                          </Badge>
                        ) : (
                          <Badge variant="destructive" className="font-normal text-[11px]">
                            Deactivated
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-8 text-xs gap-1"
                            onClick={() => handleOpenEditPermissions(role)}
                          >
                            <Key className="h-3.5 w-3.5" />
                            <span>
                              {role.isSystem || !canManageRoles ? 'View Permissions' : 'Permissions'}
                            </span>
                          </Button>

                          {!role.isSystem && (
                            <PermissionGuard permission="identity.roles.manage">
                              <Button
                                variant="outline"
                                size="sm"
                                className="h-8 text-xs"
                                aria-label={`Edit role ${role.name}`}
                                onClick={() => handleOpenEditDetails(role)}
                              >
                                <Pencil className="h-3.5 w-3.5" />
                              </Button>

                              <Button
                                variant="outline"
                                size="sm"
                                className="h-8 text-xs text-red-600 hover:text-red-700 hover:bg-red-50 border-red-100"
                                aria-label={`Delete role ${role.name}`}
                                onClick={() => {
                                  setDeleteError(null);
                                  setDeleteConfirmRole(role);
                                }}
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </Button>
                            </PermissionGuard>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Edit Custom Role Details Dialog */}
      {editDetailsRole && (
        <Dialog
          open={!!editDetailsRole}
          onOpenChange={(open) => !open && setEditDetailsRole(null)}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Edit Custom Role: {editDetailsRole.code}</DialogTitle>
              <DialogDescription>
                Update the display name and description. Role codes are immutable after creation.
              </DialogDescription>
            </DialogHeader>

            <form onSubmit={handleSaveRoleDetails} className="space-y-4 py-2">
              {editDetailsError && (
                <div role="alert" className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                  {editDetailsError}
                </div>
              )}

              <div className="space-y-1.5">
                <Label htmlFor="edit-role-name">Display Name *</Label>
                <Input
                  id="edit-role-name"
                  value={editDetailsName}
                  onChange={(e) => setEditDetailsName(e.target.value)}
                  required
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-role-description">Description</Label>
                <Input
                  id="edit-role-description"
                  value={editDetailsDescription}
                  onChange={(e) => setEditDetailsDescription(e.target.value)}
                />
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setEditDetailsRole(null)}
                  disabled={updateRoleMutation.isPending}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  className="bg-slate-900 hover:bg-slate-800"
                  disabled={updateRoleMutation.isPending}
                >
                  {updateRoleMutation.isPending && (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  )}
                  Save Changes
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      )}

      {/* Edit / View Permissions Dialog */}
      {editRole && (
        <Dialog open={!!editRole} onOpenChange={(open) => !open && setEditRole(null)}>
          <DialogContent className="max-w-3xl">
            <DialogHeader>
              <div className="flex items-center justify-between">
                <div>
                  <DialogTitle className="flex items-center gap-2">
                    <span>Role Permissions: {editRole.name}</span>
                    <Badge variant={editRole.isSystem ? 'secondary' : 'outline'}>
                      {editRole.code}
                    </Badge>
                  </DialogTitle>
                  <DialogDescription>
                    {editRole.isSystem
                      ? 'System roles are strictly immutable by architectural design.'
                      : !canManageRoles
                      ? 'Read-only view. Editing requires identity.roles.manage permission.'
                      : 'Configure granted permissions. Changes are applied atomically.'}
                  </DialogDescription>
                </div>
              </div>
            </DialogHeader>

            {editError && (
              <div role="alert" className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                {editError}
              </div>
            )}

            {editRole.isSystem && (
              <div className="rounded-md bg-slate-50 p-3 text-xs text-slate-600 border border-slate-200 flex items-center gap-2">
                <Info className="h-4 w-4 text-slate-400 shrink-0" />
                <span>
                  {editRole.code === 'OWNER'
                    ? 'The OWNER role implicitly holds all current and future system permissions.'
                    : 'Built-in system roles maintain standardized permission boundaries across all tenants.'}
                </span>
              </div>
            )}

            <div className="border border-slate-200 rounded-lg p-4 max-h-96 overflow-y-auto space-y-4 bg-slate-50/50">
              {catalogLoading ? (
                <div className="flex items-center justify-center py-6 text-xs text-slate-500">
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Loading permission catalog...
                </div>
              ) : catalogError ? (
                <div role="alert" className="text-xs text-red-600 p-2">
                  {parseApiError(catalogError, 'Failed to load permission catalog.').message}
                </div>
              ) : (
                Object.entries(permissionsByModule).map(([moduleName, perms]) => (
                  <div key={moduleName} className="space-y-2">
                    <div className="text-xs font-semibold uppercase text-slate-500 tracking-wider">
                      {getModuleLabel(moduleName)}
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                      {perms.map((p) => {
                        const isChecked =
                          editRole.code === 'OWNER' || editingPermissions.includes(p.code);
                        const wasOriginallyOnRole = (editRole.permissions ?? []).includes(p.code);
                        const actorHasIt = myPermissions
                          ? actorTenantPermissions.includes(p.code)
                          : false;
                        const cannotGrantNew =
                          !isEditPermissionsReadOnly &&
                          (!myPermissions || (!actorHasIt && !wasOriginallyOnRole && !isChecked));
                        const disabled = isEditPermissionsReadOnly || cannotGrantNew;

                        return (
                          <label
                            key={p.id}
                            className={`flex items-start gap-2 p-2 rounded-md border text-xs transition-colors ${
                              isChecked
                                ? 'bg-slate-900 text-white border-slate-900'
                                : 'bg-white text-slate-700 border-slate-200'
                            } ${
                              disabled
                                ? 'opacity-80 cursor-default'
                                : 'cursor-pointer hover:bg-slate-100'
                            }`}
                          >
                            <input
                              type="checkbox"
                              disabled={disabled}
                              className="mt-0.5 rounded border-slate-300"
                              checked={isChecked}
                              onChange={() => togglePermission(p.code, false)}
                            />
                            <div className="min-w-0">
                              <div className="font-medium">{getPermissionLabel(p)}</div>
                              <div
                                className={`text-[10px] font-mono break-all ${
                                  isChecked ? 'text-slate-300' : 'text-slate-400'
                                }`}
                              >
                                {p.code}
                              </div>
                              {p.description && (
                                <div
                                  className={`text-[10px] mt-0.5 ${
                                    isChecked ? 'text-slate-300' : 'text-slate-500'
                                  }`}
                                >
                                  {p.description}
                                </div>
                              )}
                              {!isEditPermissionsReadOnly && myPermissions && !actorHasIt && (
                                <div className="text-[10px] text-amber-500 font-medium mt-0.5">
                                  * You do not hold this permission
                                </div>
                              )}
                            </div>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                ))
              )}
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={() => setEditRole(null)}>
                {isEditPermissionsReadOnly ? 'Close' : 'Cancel'}
              </Button>
              {!isEditPermissionsReadOnly && (
                <Button
                  className="bg-slate-900 hover:bg-slate-800"
                  onClick={handleSavePermissions}
                  disabled={updatePermissionsMutation.isPending}
                >
                  {updatePermissionsMutation.isPending && (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  )}
                  Save Permissions
                </Button>
              )}
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}

      {/* Delete / Deactivate Confirm Dialog */}
      {deleteConfirmRole && (
        <Dialog
          open={!!deleteConfirmRole}
          onOpenChange={(open) => !open && setDeleteConfirmRole(null)}
        >
          <DialogContent>
            <DialogHeader>
              <div className="flex items-center gap-2 text-red-600">
                <AlertTriangle className="h-5 w-5" />
                <DialogTitle>Delete or Deactivate Role</DialogTitle>
              </div>
              <DialogDescription>
                Are you sure you want to delete custom role{' '}
                <strong className="text-slate-900">{deleteConfirmRole.name}</strong> (
                {deleteConfirmRole.code})?
              </DialogDescription>
            </DialogHeader>

            <div className="rounded-md bg-amber-50 p-3 text-xs text-amber-800 border border-amber-200 space-y-1">
              <div className="font-semibold">Audit Lineage Guarantee:</div>
              <div>
                If this role has historical user assignments, it will be automatically
                soft-deactivated (`isActive = false`) to preserve audit history and avoid foreign-key
                corruption.
              </div>
            </div>

            {deleteError && (
              <div role="alert" className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                {deleteError}
              </div>
            )}

            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setDeleteConfirmRole(null)}
                disabled={deleteRoleMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                variant="destructive"
                onClick={handleDeleteRole}
                disabled={deleteRoleMutation.isPending}
              >
                {deleteRoleMutation.isPending && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                Confirm Delete / Deactivate
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
};
