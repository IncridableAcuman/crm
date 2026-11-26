import React, { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  UnitOfMeasure,
  useCreateUnit,
  useDeleteUnit,
  useUnits,
  useUpdateUnit,
  useUpdateUnitStatus,
} from '@/entities/unit';
import {
  PermissionGuard,
  useCatalogReadBranchScope,
  useHasAnyScopePermission,
} from '@/entities/permission';
import { parseApiError } from '@/shared/api/errorHandler';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog';
import {
  AlertCircle,
  Edit3,
  Loader2,
  Lock,
  Plus,
  Power,
  RefreshCw,
  Ruler,
  Trash2,
} from 'lucide-react';

const unitCreateSchema = z
  .object({
    code: z
      .string()
      .trim()
      .min(1, 'Code must be 1 to 20 characters.')
      .max(20, 'Code must be 1 to 20 characters.')
      .regex(/^[A-Za-z0-9_-]{1,20}$/, "Code may only contain letters, digits, '_', and '-'."),
    name: z
      .string()
      .trim()
      .min(1, 'Unit name is required.')
      .max(80, 'Unit name cannot exceed 80 characters.'),
    symbol: z
      .string()
      .trim()
      .min(1, 'Symbol is required.')
      .max(15, 'Symbol cannot exceed 15 characters.'),
    allowDecimal: z.boolean(),
    decimalPrecision: z.coerce.number().int().min(0).max(3),
  })
  .refine((v) => v.allowDecimal || v.decimalPrecision === 0, {
    message: 'Integer units must have decimal precision 0.',
    path: ['decimalPrecision'],
  });

const unitEditSchema = unitCreateSchema.innerType().omit({ code: true }).refine(
  (v) => v.allowDecimal || v.decimalPrecision === 0,
  {
    message: 'Integer units must have decimal precision 0.',
    path: ['decimalPrecision'],
  }
);

type UnitCreateValues = z.infer<typeof unitCreateSchema>;
type UnitEditValues = z.infer<typeof unitEditSchema>;

export interface UnitManagerPanelProps {
  branchId?: string;
}

export const UnitManagerPanel: React.FC<UnitManagerPanelProps> = ({ branchId }) => {
  const canReadUnits = useHasAnyScopePermission('catalog.products.read');
  const unitScope = useCatalogReadBranchScope('catalog.products.read', branchId);
  const [includeInactive, setIncludeInactive] = useState(false);

  const {
    data: units = [],
    isLoading,
    isError,
    error,
    refetch,
  } = useUnits({ includeInactive, branchId, enabled: canReadUnits && unitScope.canRead });

  const createMutation = useCreateUnit();
  const updateMutation = useUpdateUnit();
  const statusMutation = useUpdateUnitStatus();
  const deleteMutation = useDeleteUnit();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingUnit, setEditingUnit] = useState<UnitOfMeasure | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<UnitOfMeasure | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const createForm = useForm<UnitCreateValues>({
    resolver: zodResolver(unitCreateSchema),
    defaultValues: {
      code: '',
      name: '',
      symbol: '',
      allowDecimal: false,
      decimalPrecision: 0,
    },
  });

  const editForm = useForm<UnitEditValues>({
    resolver: zodResolver(unitEditSchema),
    defaultValues: {
      name: '',
      symbol: '',
      allowDecimal: false,
      decimalPrecision: 0,
    },
  });

  const createAllowDecimal = createForm.watch('allowDecimal');
  const editAllowDecimal = editForm.watch('allowDecimal');

  useEffect(() => {
    if (!createAllowDecimal) {
      createForm.setValue('decimalPrecision', 0);
    }
  }, [createAllowDecimal, createForm]);

  useEffect(() => {
    if (!editAllowDecimal) {
      editForm.setValue('decimalPrecision', 0);
    }
  }, [editAllowDecimal, editForm]);

  const openCreate = () => {
    setEditingUnit(null);
    setFormError(null);
    createForm.reset({
      code: '',
      name: '',
      symbol: '',
      allowDecimal: false,
      decimalPrecision: 0,
    });
    setDialogOpen(true);
  };

  const openEdit = (unit: UnitOfMeasure) => {
    setEditingUnit(unit);
    setFormError(null);
    editForm.reset({
      name: unit.name,
      symbol: unit.symbol,
      allowDecimal: unit.allowDecimal,
      decimalPrecision: unit.decimalPrecision,
    });
    setDialogOpen(true);
  };

  const handleCreateSubmit = createForm.handleSubmit(async (values) => {
    setFormError(null);
    try {
      await createMutation.mutateAsync({
        code: values.code.trim().toUpperCase(),
        name: values.name.trim(),
        symbol: values.symbol.trim(),
        allowDecimal: values.allowDecimal,
        decimalPrecision: values.allowDecimal ? values.decimalPrecision : 0,
      });
      setDialogOpen(false);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to create unit of measure.');
      for (const [field, message] of Object.entries(parsed.fieldErrors)) {
        if (field === 'code' || field === 'name' || field === 'symbol' || field === 'decimalPrecision') {
          createForm.setError(field, { message });
        }
      }
      setFormError(parsed.message);
    }
  });

  const handleEditSubmit = editForm.handleSubmit(async (values) => {
    if (!editingUnit) return;
    setFormError(null);
    try {
      await updateMutation.mutateAsync({
        unitId: editingUnit.id,
        data: {
          name: values.name.trim(),
          symbol: values.symbol.trim(),
          allowDecimal: values.allowDecimal,
          decimalPrecision: values.allowDecimal ? values.decimalPrecision : 0,
        },
      });
      setDialogOpen(false);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to update unit of measure.');
      for (const [field, message] of Object.entries(parsed.fieldErrors)) {
        if (field === 'name' || field === 'symbol' || field === 'decimalPrecision') {
          editForm.setError(field, { message });
        }
      }
      setFormError(parsed.message);
    }
  });

  const handleToggleStatus = async (unit: UnitOfMeasure) => {
    if (statusMutation.isPending) return;
    setActionError(null);
    try {
      await statusMutation.mutateAsync({ unitId: unit.id, isActive: !unit.isActive });
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to update unit status.').message);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setActionError(null);
    try {
      await deleteMutation.mutateAsync(deleteTarget.id);
      setDeleteTarget(null);
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to delete unit of measure.').message);
      setDeleteTarget(null);
    }
  };

  if (!canReadUnits) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Ruler className="h-4 w-4" />
            <span>Units of Measure</span>
          </CardTitle>
          <CardDescription>
            Requires <code className="font-mono text-xs">catalog.products.read</code> permission to view units.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  if (unitScope.invalidBranchRequested) {
    return (
      <Card className="border-amber-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Ruler className="h-4 w-4 text-amber-600" />
            <span>Units of Measure</span>
          </CardTitle>
          <CardDescription>
            You do not have <code className="font-mono text-xs">catalog.products.read</code> permission for the requested branch.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <CardTitle className="flex items-center gap-2 text-base">
            <Ruler className="h-4 w-4 text-slate-700" />
            <span>Units of Measure</span>
          </CardTitle>
          <CardDescription className="text-xs">
            System-seeded units (PCS, KG, L, etc.) are immutable. Custom units can be created and managed by administrators.
          </CardDescription>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
            <input
              type="checkbox"
              checked={includeInactive}
              onChange={(e) => setIncludeInactive(e.target.checked)}
              className="rounded border-slate-300"
            />
            <span>Show inactive</span>
          </label>

          <PermissionGuard permission="catalog.categories.manage">
            <Button size="sm" onClick={openCreate} className="gap-1.5 bg-slate-900 hover:bg-slate-800">
              <Plus className="h-4 w-4" />
              <span>New Custom Unit</span>
            </Button>
          </PermissionGuard>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        {actionError && (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700"
          >
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>{actionError}</span>
          </div>
        )}

        {isLoading ? (
          <div className="flex items-center justify-center py-10 text-sm text-slate-500 gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>Loading units of measure...</span>
          </div>
        ) : isError ? (
          <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 space-y-2">
            <div>{parseApiError(error, 'Unable to load units of measure.').message}</div>
            <Button variant="outline" size="sm" onClick={() => void refetch()} className="gap-1.5">
              <RefreshCw className="h-3.5 w-3.5" />
              <span>Retry</span>
            </Button>
          </div>
        ) : units.length === 0 ? (
          <div className="py-8 text-center text-sm text-slate-500">
            No units of measure found.
          </div>
        ) : (
          <Table className="min-w-[720px]">
            <TableHeader>
              <TableRow>
                <TableHead>Code</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Symbol</TableHead>
                <TableHead>Precision</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {units.map((unit) => (
                <TableRow key={unit.id}>
                  <TableCell className="font-mono text-xs font-semibold">{unit.code}</TableCell>
                  <TableCell className="font-medium text-slate-900">{unit.name}</TableCell>
                  <TableCell className="font-mono text-xs">{unit.symbol}</TableCell>
                  <TableCell className="text-xs text-slate-600">
                    {unit.allowDecimal ? `Decimal (${unit.decimalPrecision} dec)` : 'Integer only'}
                  </TableCell>
                  <TableCell>
                    {unit.isSystem ? (
                      <Badge variant="secondary" className="gap-1 text-[10px]">
                        <Lock className="h-3 w-3" />
                        System
                      </Badge>
                    ) : (
                      <Badge variant="outline" className="text-[10px]">Custom</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    <Badge variant={unit.isActive ? 'success' : 'secondary'} className="text-[10px]">
                      {unit.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    {unit.isSystem ? (
                      <span className="text-xs text-slate-400">Immutable</span>
                    ) : (
                      <PermissionGuard permission="catalog.categories.manage">
                        <div className="flex items-center justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => openEdit(unit)}
                            aria-label={`Edit unit ${unit.code}`}
                            className="h-7 px-2 text-xs"
                          >
                            <Edit3 className="h-3.5 w-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={statusMutation.isPending}
                            onClick={() => void handleToggleStatus(unit)}
                            aria-label={`${unit.isActive ? 'Deactivate' : 'Activate'} unit ${unit.code}`}
                            className="h-7 px-2 text-xs"
                          >
                            <Power className="h-3.5 w-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setDeleteTarget(unit)}
                            aria-label={`Delete unit ${unit.code}`}
                            className="h-7 px-2 text-xs text-red-600 hover:text-red-700 hover:bg-red-50"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </PermissionGuard>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>

      {/* Create / Edit Unit Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {editingUnit ? `Edit Custom Unit: ${editingUnit.code}` : 'Create Custom Unit'}
            </DialogTitle>
            <DialogDescription>
              {editingUnit
                ? 'Update custom unit display name, symbol, and decimal quantity precision.'
                : 'Add a tenant-custom unit of measure (e.g., PACK, ROLL, TRAY).'}
            </DialogDescription>
          </DialogHeader>

          {formError && (
            <div
              role="alert"
              className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700"
            >
              <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
              <span>{formError}</span>
            </div>
          )}

          {!editingUnit ? (
            <form onSubmit={handleCreateSubmit} className="space-y-4">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="unit-code">Code *</Label>
                  <Input
                    id="unit-code"
                    placeholder="TRAY"
                    className="uppercase font-mono"
                    {...createForm.register('code')}
                  />
                  {createForm.formState.errors.code && (
                    <p className="text-xs text-red-600">{createForm.formState.errors.code.message}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="unit-symbol">Symbol *</Label>
                  <Input id="unit-symbol" placeholder="tr" {...createForm.register('symbol')} />
                  {createForm.formState.errors.symbol && (
                    <p className="text-xs text-red-600">
                      {createForm.formState.errors.symbol.message}
                    </p>
                  )}
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="unit-name">Name *</Label>
                <Input id="unit-name" placeholder="Tray" {...createForm.register('name')} />
                {createForm.formState.errors.name && (
                  <p className="text-xs text-red-600">{createForm.formState.errors.name.message}</p>
                )}
              </div>

              <div className="flex flex-col gap-3 rounded-md border border-slate-200 p-3 sm:flex-row sm:items-center sm:justify-between">
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="checkbox" {...createForm.register('allowDecimal')} />
                  <span>Allow decimal quantities</span>
                </label>

                <div className="flex items-center gap-2">
                  <Label htmlFor="unit-prec" className="text-xs">
                    Decimals (0–3)
                  </Label>
                  <Input
                    id="unit-prec"
                    type="number"
                    min={0}
                    max={3}
                    disabled={!createAllowDecimal}
                    className="w-20 h-8"
                    {...createForm.register('decimalPrecision')}
                  />
                </div>
              </div>

              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  disabled={createMutation.isPending}
                  className="bg-slate-900 hover:bg-slate-800"
                >
                  {createMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Create Unit
                </Button>
              </DialogFooter>
            </form>
          ) : (
            <form onSubmit={handleEditSubmit} className="space-y-4">
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="edit-unit-name">Name *</Label>
                  <Input id="edit-unit-name" {...editForm.register('name')} />
                  {editForm.formState.errors.name && (
                    <p className="text-xs text-red-600">{editForm.formState.errors.name.message}</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="edit-unit-symbol">Symbol *</Label>
                  <Input id="edit-unit-symbol" {...editForm.register('symbol')} />
                  {editForm.formState.errors.symbol && (
                    <p className="text-xs text-red-600">
                      {editForm.formState.errors.symbol.message}
                    </p>
                  )}
                </div>
              </div>

              <div className="flex flex-col gap-3 rounded-md border border-slate-200 p-3 sm:flex-row sm:items-center sm:justify-between">
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="checkbox" {...editForm.register('allowDecimal')} />
                  <span>Allow decimal quantities</span>
                </label>

                <div className="flex items-center gap-2">
                  <Label htmlFor="edit-unit-prec" className="text-xs">
                    Decimals (0–3)
                  </Label>
                  <Input
                    id="edit-unit-prec"
                    type="number"
                    min={0}
                    max={3}
                    disabled={!editAllowDecimal}
                    className="w-20 h-8"
                    {...editForm.register('decimalPrecision')}
                  />
                </div>
              </div>

              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
                  Cancel
                </Button>
                <Button
                  type="submit"
                  disabled={updateMutation.isPending}
                  className="bg-slate-900 hover:bg-slate-800"
                >
                  {updateMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Save Changes
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={!!deleteTarget} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete Custom Unit</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete custom unit{' '}
              <span className="font-mono font-semibold">{deleteTarget?.code}</span>? Units referenced by products cannot be deleted.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => void handleDelete()}
            >
              {deleteMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Delete Unit
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
};
