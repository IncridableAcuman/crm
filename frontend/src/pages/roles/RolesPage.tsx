import React, { useState } from 'react';
import {
  useRoles,
  useCreateRole,
  useUpdateRolePermissions,
  useDeleteRole,
  Role,
} from '@/entities/role';
import { usePermissionsCatalog, useMyPermissions, PermissionGuard } from '@/entities/permission';
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
} from 'lucide-react';
import { AxiosError } from 'axios';

export const RolesPage: React.FC = () => {
  const { data: roles = [], isLoading, error } = useRoles();
  const { data: catalog = [] } = usePermissionsCatalog();
  const { data: myPermissions } = useMyPermissions();

  const createRoleMutation = useCreateRole();
  const updatePermissionsMutation = useUpdateRolePermissions();
  const deleteRoleMutation = useDeleteRole();

  // Create Role State
  const [createOpen, setCreateOpen] = useState(false);
  const [newCode, setNewCode] = useState('');
  const [newName, setNewName] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([]);
  const [createError, setCreateError] = useState<string | null>(null);

  // Edit Permissions State
  const [editRole, setEditRole] = useState<Role | null>(null);
  const [editingPermissions, setEditingPermissions] = useState<string[]>([]);
  const [editError, setEditError] = useState<string | null>(null);

  // Delete Confirm State
  const [deleteConfirmRole, setDeleteConfirmRole] = useState<Role | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Group catalog by module
  const permissionsByModule = catalog.reduce<Record<string, typeof catalog>>((acc, perm) => {
    const mod = perm.module || 'other';
    if (!acc[mod]) acc[mod] = [];
    acc[mod].push(perm);
    return acc;
  }, {});

  const handleCreateRole = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);

    if (!newCode.trim() || !newName.trim()) {
      setCreateError('Role code and name are required.');
      return;
    }

    try {
      await createRoleMutation.mutateAsync({
        code: newCode.trim().toUpperCase(),
        name: newName.trim(),
        description: newDescription.trim() || null,
        permissions: selectedPermissions,
      });
      setNewCode('');
      setNewName('');
      setNewDescription('');
      setSelectedPermissions([]);
      setCreateOpen(false);
    } catch (err: unknown) {
      if (err instanceof AxiosError && err.response?.data?.detail) {
        setCreateError(err.response.data.detail);
      } else if (err instanceof AxiosError && err.response?.data?.message) {
        setCreateError(err.response.data.message);
      } else {
        setCreateError('Failed to create role. Ensure you hold all selected permissions.');
      }
    }
  };

  const handleOpenEditPermissions = (role: Role) => {
    setEditRole(role);
    setEditingPermissions(role.permissions || []);
    setEditError(null);
  };

  const handleSavePermissions = async () => {
    if (!editRole) return;
    setEditError(null);

    try {
      await updatePermissionsMutation.mutateAsync({
        roleId: editRole.id,
        permissions: editingPermissions,
      });
      setEditRole(null);
    } catch (err: unknown) {
      if (err instanceof AxiosError && err.response?.data?.detail) {
        setEditError(err.response.data.detail);
      } else if (err instanceof AxiosError && err.response?.data?.message) {
        setEditError(err.response.data.message);
      } else {
        setEditError('Failed to update permissions (Grant-What-You-Hold violation).');
      }
    }
  };

  const handleDeleteRole = async () => {
    if (!deleteConfirmRole) return;
    setDeleteError(null);

    try {
      await deleteRoleMutation.mutateAsync(deleteConfirmRole.id);
      setDeleteConfirmRole(null);
    } catch (err: unknown) {
      if (err instanceof AxiosError && err.response?.data?.detail) {
        setDeleteError(err.response.data.detail);
      } else {
        setDeleteError('Failed to delete role.');
      }
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
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
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
                  Define a new role and grant specific permissions. (Grant-What-You-Hold applies).
                </DialogDescription>
              </DialogHeader>

              <form onSubmit={handleCreateRole} className="space-y-4 py-2">
                {createError && (
                  <div className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
                    {createError}
                  </div>
                )}

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="role-code">Role Code *</Label>
                    <Input
                      id="role-code"
                      placeholder="e.g. SUPERVISOR"
                      value={newCode}
                      onChange={(e) => setNewCode(e.target.value.toUpperCase())}
                      required
                    />
                    <span className="text-[11px] text-slate-400">Uppercase identifier</span>
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
                      {selectedPermissions.length} selected
                    </span>
                  </div>

                  <div className="border border-slate-200 rounded-lg p-3 max-h-60 overflow-y-auto space-y-4 bg-slate-50/50">
                    {Object.entries(permissionsByModule).map(([moduleName, perms]) => (
                      <div key={moduleName} className="space-y-2">
                        <div className="text-xs font-semibold uppercase text-slate-500 tracking-wider">
                          {moduleName} Module
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                          {perms.map((p) => {
                            const isChecked = selectedPermissions.includes(p.code);
                            const actorHasIt = myPermissions?.tenantPermissions?.includes(p.code);
                            return (
                              <label
                                key={p.id}
                                className={`flex items-start gap-2 p-2 rounded-md border text-xs cursor-pointer transition-colors ${
                                  isChecked
                                    ? 'bg-slate-900 text-white border-slate-900'
                                    : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-100'
                                }`}
                              >
                                <input
                                  type="checkbox"
                                  className="mt-0.5 rounded border-slate-300"
                                  checked={isChecked}
                                  onChange={() => togglePermission(p.code, true)}
                                />
                                <div>
                                  <div className="font-medium">{p.name}</div>
                                  <div
                                    className={`text-[10px] ${
                                      isChecked ? 'text-slate-300' : 'text-slate-400'
                                    }`}
                                  >
                                    {p.code}
                                  </div>
                                  {!actorHasIt && (
                                    <div className="text-[10px] text-amber-500 font-medium">
                                      * You do not actively hold this permission
                                    </div>
                                  )}
                                </div>
                              </label>
                            );
                          })}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <DialogFooter className="pt-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setCreateOpen(false)}
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
            <div className="p-4 text-center text-sm text-red-600 bg-red-50 rounded-lg">
              Failed to load roles. Ensure you have the `identity.roles.read` permission.
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
                {roles.map((role) => (
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
                        ? 'All (Implicit 21/21)'
                        : `${role.permissions?.length || 0} permissions`}
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
                          <span>{role.isSystem ? 'View Permissions' : 'Permissions'}</span>
                        </Button>

                        {!role.isSystem && (
                          <PermissionGuard permission="identity.roles.manage">
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-8 text-xs text-red-600 hover:text-red-700 hover:bg-red-50 border-red-100"
                              onClick={() => setDeleteConfirmRole(role)}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          </PermissionGuard>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

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
                      : 'Configure granted permissions. Changes are applied atomically.'}
                  </DialogDescription>
                </div>
              </div>
            </DialogHeader>

            {editError && (
              <div className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
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
              {Object.entries(permissionsByModule).map(([moduleName, perms]) => (
                <div key={moduleName} className="space-y-2">
                  <div className="text-xs font-semibold uppercase text-slate-500 tracking-wider">
                    {moduleName} Module
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                    {perms.map((p) => {
                      const isChecked =
                        editRole.code === 'OWNER' || editingPermissions.includes(p.code);
                      const actorHasIt = myPermissions?.tenantPermissions?.includes(p.code);

                      return (
                        <label
                          key={p.id}
                          className={`flex items-start gap-2 p-2 rounded-md border text-xs transition-colors ${
                            isChecked
                              ? 'bg-slate-900 text-white border-slate-900'
                              : 'bg-white text-slate-700 border-slate-200'
                          } ${
                            editRole.isSystem
                              ? 'opacity-80 cursor-default'
                              : 'cursor-pointer hover:bg-slate-100'
                          }`}
                        >
                          <input
                            type="checkbox"
                            disabled={editRole.isSystem}
                            className="mt-0.5 rounded border-slate-300"
                            checked={isChecked}
                            onChange={() => togglePermission(p.code, false)}
                          />
                          <div>
                            <div className="font-medium">{p.name}</div>
                            <div
                              className={`text-[10px] ${
                                isChecked ? 'text-slate-300' : 'text-slate-400'
                              }`}
                            >
                              {p.code}
                            </div>
                            {!editRole.isSystem && !actorHasIt && (
                              <div className="text-[10px] text-amber-500 font-medium">
                                * Warning: You do not hold this permission
                              </div>
                            )}
                          </div>
                        </label>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={() => setEditRole(null)}>
                {editRole.isSystem ? 'Close' : 'Cancel'}
              </Button>
              {!editRole.isSystem && (
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
              <div className="rounded-md bg-red-50 p-3 text-xs text-red-700 border border-red-200">
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
