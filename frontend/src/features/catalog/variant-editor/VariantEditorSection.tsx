import React, { useState } from 'react';
import {
  BarcodeType,
  ProductBarcode,
  ProductDetail,
  ProductVariant,
  useAddBarcode,
  useAddVariant,
  useChangeDefaultVariant,
  useConvertProductToVariable,
  useRemoveBarcode,
  useSetPrimaryBarcode,
  useUpdateBarcode,
  useUpdateVariant,
  useUpdateVariantStatus,
} from '@/entities/product';
import { PermissionGuard } from '@/entities/permission';
import { parseApiError } from '@/shared/api/errorHandler';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
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
  Barcode,
  Edit3,
  Layers,
  Loader2,
  Plus,
  Power,
  Star,
  Trash2,
} from 'lucide-react';

const BARCODE_TYPES: BarcodeType[] = ['EAN13', 'EAN8', 'UPC_A', 'CODE128', 'INTERNAL', 'OTHER'];

export interface VariantEditorSectionProps {
  product: ProductDetail;
}

export const VariantEditorSection: React.FC<VariantEditorSectionProps> = ({ product }) => {
  const isArchived = product.status === 'ARCHIVED';

  const addVariantMutation = useAddVariant();
  const updateVariantMutation = useUpdateVariant();
  const changeDefaultMutation = useChangeDefaultVariant();
  const updateStatusMutation = useUpdateVariantStatus();
  const convertMutation = useConvertProductToVariable();

  const addBarcodeMutation = useAddBarcode();
  const updateBarcodeMutation = useUpdateBarcode();
  const setPrimaryBarcodeMutation = useSetPrimaryBarcode();
  const removeBarcodeMutation = useRemoveBarcode();

  const [actionError, setActionError] = useState<string | null>(null);

  // Variant Modal State
  const [variantModalOpen, setVariantModalOpen] = useState(false);
  const [editingVariant, setEditingVariant] = useState<ProductVariant | null>(null);
  const [vSku, setVSku] = useState('');
  const [vName, setVName] = useState('');
  const [vCost, setVCost] = useState('0');
  const [vSell, setVSell] = useState('0');
  const [vIsDefault, setVIsDefault] = useState(false);
  const [vOptions, setVOptions] = useState<Record<string, string>>({});
  const [vFormError, setVFormError] = useState<string | null>(null);

  // Barcode Modal State
  const [barcodeModalOpen, setBarcodeModalOpen] = useState(false);
  const [barcodeVariantId, setBarcodeVariantId] = useState<string | null>(null);
  const [editingBarcode, setEditingBarcode] = useState<ProductBarcode | null>(null);
  const [deleteBarcodeTarget, setDeleteBarcodeTarget] = useState<{
    variantId: string;
    barcode: ProductBarcode;
  } | null>(null);
  const [bValue, setBValue] = useState('');
  const [bType, setBType] = useState<BarcodeType>('EAN13');
  const [bPrimary, setBPrimary] = useState(false);
  const [bFormError, setBFormError] = useState<string | null>(null);

  const isInlineMutating =
    changeDefaultMutation.isPending ||
    updateStatusMutation.isPending ||
    setPrimaryBarcodeMutation.isPending ||
    removeBarcodeMutation.isPending;

  // Convert to Variable Modal State
  const [convertOpen, setConvertOpen] = useState(false);
  const [convertOptionsText, setConvertOptionsText] = useState('Size');
  const [convertDefaults, setConvertDefaults] = useState<Record<string, string>>({ Size: 'Standard' });
  const [convertError, setConvertError] = useState<string | null>(null);

  const openAddVariant = () => {
    setEditingVariant(null);
    setVSku('');
    setVName('');
    setVCost('0');
    setVSell('0');
    setVIsDefault(false);
    const opts: Record<string, string> = {};
    product.variantOptions.forEach((opt) => {
      opts[opt] = '';
    });
    setVOptions(opts);
    setVFormError(null);
    setVariantModalOpen(true);
  };

  const openEditVariant = (variant: ProductVariant) => {
    setEditingVariant(variant);
    setVSku(variant.sku);
    setVName(variant.name);
    setVCost(String(variant.costPrice));
    setVSell(String(variant.sellingPrice));
    setVIsDefault(variant.isDefault);
    setVOptions({ ...variant.optionValues });
    setVFormError(null);
    setVariantModalOpen(true);
  };

  const handleVariantSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setVFormError(null);
    const parsedCost = Number(vCost);
    const parsedSell = Number(vSell);
    if (!Number.isFinite(parsedCost) || parsedCost < 0) {
      setVFormError('Cost price must be a non-negative number.');
      return;
    }
    if (!Number.isFinite(parsedSell) || parsedSell < 0) {
      setVFormError('Selling price must be a non-negative number.');
      return;
    }
    try {
      if (editingVariant) {
        await updateVariantMutation.mutateAsync({
          productId: product.id,
          variantId: editingVariant.id,
          data: {
            sku: vSku.trim().toUpperCase(),
            name: vName.trim(),
            optionValues: product.productType === 'VARIABLE' ? vOptions : {},
            costPrice: parsedCost,
            sellingPrice: parsedSell,
          },
        });
      } else {
        await addVariantMutation.mutateAsync({
          productId: product.id,
          data: {
            sku: vSku.trim().toUpperCase(),
            name: vName.trim(),
            optionValues: vOptions,
            costPrice: parsedCost,
            sellingPrice: parsedSell,
            isDefault: vIsDefault,
            barcodes: [],
          },
        });
      }
      setVariantModalOpen(false);
    } catch (err) {
      setVFormError(parseApiError(err, 'Failed to save variant.').message);
    }
  };

  const handleSetDefault = async (variantId: string) => {
    if (isInlineMutating) return;
    setActionError(null);
    try {
      await changeDefaultMutation.mutateAsync({ productId: product.id, variantId });
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to set default variant.').message);
    }
  };

  const handleToggleVariantStatus = async (variant: ProductVariant) => {
    if (isInlineMutating) return;
    setActionError(null);
    try {
      await updateStatusMutation.mutateAsync({
        productId: product.id,
        variantId: variant.id,
        isActive: !variant.isActive,
      });
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to toggle variant status.').message);
    }
  };

  const openAddBarcode = (variant: ProductVariant) => {
    setBarcodeVariantId(variant.id);
    setEditingBarcode(null);
    setBValue('');
    setBType('EAN13');
    setBPrimary(variant.barcodes.length === 0);
    setBFormError(null);
    setBarcodeModalOpen(true);
  };

  const openEditBarcode = (variantId: string, barcode: ProductBarcode) => {
    setBarcodeVariantId(variantId);
    setEditingBarcode(barcode);
    setBValue(barcode.barcode);
    setBType(barcode.barcodeType);
    setBPrimary(barcode.isPrimary);
    setBFormError(null);
    setBarcodeModalOpen(true);
  };

  const handleBarcodeSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!barcodeVariantId) return;
    setBFormError(null);
    try {
      if (editingBarcode) {
        await updateBarcodeMutation.mutateAsync({
          productId: product.id,
          variantId: barcodeVariantId,
          barcodeId: editingBarcode.id,
          data: {
            barcode: bValue.trim(),
            barcodeType: bType,
            isPrimary: bPrimary,
          },
        });
      } else {
        await addBarcodeMutation.mutateAsync({
          productId: product.id,
          variantId: barcodeVariantId,
          data: {
            barcode: bValue.trim(),
            barcodeType: bType,
            isPrimary: bPrimary,
          },
        });
      }
      setBarcodeModalOpen(false);
    } catch (err) {
      setBFormError(parseApiError(err, 'Failed to save barcode.').message);
    }
  };

  const handleSetPrimaryBarcode = async (variantId: string, barcodeId: string) => {
    if (isInlineMutating) return;
    setActionError(null);
    try {
      await setPrimaryBarcodeMutation.mutateAsync({ productId: product.id, variantId, barcodeId });
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to set primary barcode.').message);
    }
  };

  const handleConfirmRemoveBarcode = async () => {
    if (!deleteBarcodeTarget || removeBarcodeMutation.isPending) return;
    setActionError(null);
    try {
      await removeBarcodeMutation.mutateAsync({
        productId: product.id,
        variantId: deleteBarcodeTarget.variantId,
        barcodeId: deleteBarcodeTarget.barcode.id,
      });
      setDeleteBarcodeTarget(null);
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to remove barcode.').message);
      setDeleteBarcodeTarget(null);
    }
  };

  const parsedConvertAxes = convertOptionsText
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 3);

  const handleConvertSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setConvertError(null);
    if (parsedConvertAxes.length === 0) {
      setConvertError('Enter at least one option name (comma-separated, max 3).');
      return;
    }
    const mapping: Record<string, string> = {};
    for (const axis of parsedConvertAxes) {
      const val = convertDefaults[axis]?.trim();
      if (!val) {
        setConvertError(`Provide a value for existing default variant option "${axis}".`);
        return;
      }
      mapping[axis] = val;
    }
    try {
      await convertMutation.mutateAsync({
        productId: product.id,
        data: {
          variantOptions: parsedConvertAxes,
          existingDefaultOptionValues: mapping,
        },
      });
      setConvertOpen(false);
    } catch (err) {
      setConvertError(parseApiError(err, 'Failed to convert product to VARIABLE.').message);
    }
  };

  return (
    <Card>
      <CardHeader className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <CardTitle className="flex items-center gap-2 text-base">
            <Layers className="h-4 w-4 text-slate-700" />
            <span>Variants & Barcodes ({product.variants.length})</span>
          </CardTitle>
          <CardDescription className="text-xs">
            {isArchived
              ? 'Archived products are read-only until restored to ACTIVE status.'
              : product.productType === 'SIMPLE'
                ? 'SIMPLE product with a single default variant. Convert to VARIABLE to add option matrices.'
                : `VARIABLE product options: ${product.variantOptions.join(', ')}`}
          </CardDescription>
        </div>

        <PermissionGuard permission="catalog.products.update">
          {!isArchived && (
            <div className="flex items-center gap-2">
              {product.productType === 'SIMPLE' ? (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setConvertError(null);
                    setConvertOpen(true);
                  }}
                >
                  Convert to Variable
                </Button>
              ) : (
                <Button
                  size="sm"
                  onClick={openAddVariant}
                  className="gap-1.5 bg-slate-900 hover:bg-slate-800"
                >
                  <Plus className="h-4 w-4" />
                  <span>Add Variant</span>
                </Button>
              )}
            </div>
          )}
        </PermissionGuard>
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

        <div className="space-y-3">
          {product.variants.map((variant) => (
            <div
              key={variant.id}
              className="rounded-lg border border-slate-200 bg-white p-4 space-y-3"
            >
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div className="space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-mono text-xs font-bold text-slate-900">
                      {variant.sku}
                    </span>
                    <span className="text-sm font-medium text-slate-800">{variant.name}</span>
                    {variant.isActiveDefault ? (
                      <Badge variant="success" className="text-[10px]">
                        Active Default
                      </Badge>
                    ) : variant.isDefault ? (
                      <Badge variant="warning" className="text-[10px]">
                        Historical Default
                      </Badge>
                    ) : null}
                    <Badge variant={variant.isActive ? 'info' : 'secondary'} className="text-[10px]">
                      {variant.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                  </div>

                  {Object.keys(variant.optionValues).length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      {Object.entries(variant.optionValues).map(([k, v]) => (
                        <Badge key={k} variant="outline" className="text-[10px] font-normal">
                          {k}: {v}
                        </Badge>
                      ))}
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-4">
                  <div className="text-right text-xs">
                    <div className="font-semibold text-slate-900">
                      Sell: {Number(variant.sellingPrice).toFixed(2)}
                    </div>
                    <div className="text-slate-500">
                      Cost: {Number(variant.costPrice).toFixed(2)}
                    </div>
                  </div>

                  <PermissionGuard permission="catalog.products.update">
                    {!isArchived && (
                      <div className="flex items-center gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openEditVariant(variant)}
                          aria-label={`Edit variant ${variant.sku}`}
                          className="h-7 px-2 text-xs"
                        >
                          <Edit3 className="h-3.5 w-3.5" />
                        </Button>
                        {!variant.isActiveDefault && variant.isActive && (
                          <Button
                            variant="ghost"
                            size="sm"
                            disabled={isInlineMutating}
                            onClick={() => void handleSetDefault(variant.id)}
                            aria-label={`Set default variant ${variant.sku}`}
                            title="Set as Default"
                            className="h-7 px-2 text-xs text-amber-600"
                          >
                            <Star className="h-3.5 w-3.5" />
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={isInlineMutating}
                          onClick={() => void handleToggleVariantStatus(variant)}
                          aria-label={`${variant.isActive ? 'Deactivate' : 'Activate'} variant ${variant.sku}`}
                          className="h-7 px-2 text-xs"
                        >
                          <Power className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    )}
                  </PermissionGuard>
                </div>
              </div>

              {/* Barcodes */}
              <div className="border-t border-slate-100 pt-2.5 flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center gap-1 text-xs text-slate-500">
                    <Barcode className="h-3.5 w-3.5" />
                    Barcodes:
                  </span>
                  {variant.barcodes.length === 0 ? (
                    <span className="text-xs text-slate-400">None assigned</span>
                  ) : (
                    variant.barcodes.map((b) => (
                      <div
                        key={b.id}
                        className="inline-flex items-center gap-1.5 rounded-md border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs"
                      >
                        <span className="font-mono">{b.barcode}</span>
                        <span className="text-[10px] text-slate-400">{b.barcodeType}</span>
                        {b.isPrimary && (
                          <Badge variant="success" className="text-[9px] px-1 py-0">
                            Primary
                          </Badge>
                        )}
                        <PermissionGuard permission="catalog.products.update">
                          {!isArchived && (
                            <>
                              {!b.isPrimary && (
                                <button
                                  type="button"
                                  disabled={isInlineMutating}
                                  onClick={() => void handleSetPrimaryBarcode(variant.id, b.id)}
                                  aria-label={`Make barcode ${b.barcode} primary`}
                                  title="Make Primary"
                                  className="text-amber-600 hover:text-amber-700 disabled:opacity-50"
                                >
                                  <Star className="h-3 w-3" />
                                </button>
                              )}
                              <button
                                type="button"
                                onClick={() => openEditBarcode(variant.id, b)}
                                aria-label={`Edit barcode ${b.barcode}`}
                                title="Edit Barcode"
                                className="text-slate-500 hover:text-slate-900"
                              >
                                <Edit3 className="h-3 w-3" />
                              </button>
                              <button
                                type="button"
                                disabled={isInlineMutating}
                                onClick={() => setDeleteBarcodeTarget({ variantId: variant.id, barcode: b })}
                                aria-label={`Remove barcode ${b.barcode}`}
                                title="Remove Barcode"
                                className="text-red-500 hover:text-red-700 disabled:opacity-50"
                              >
                                <Trash2 className="h-3 w-3" />
                              </button>
                            </>
                          )}
                        </PermissionGuard>
                      </div>
                    ))
                  )}
                </div>

                <PermissionGuard permission="catalog.products.update">
                  {!isArchived && (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => openAddBarcode(variant)}
                      className="h-7 text-xs gap-1 shrink-0"
                    >
                      <Plus className="h-3 w-3" />
                      <span>Add Barcode</span>
                    </Button>
                  )}
                </PermissionGuard>
              </div>
            </div>
          ))}
        </div>
      </CardContent>

      {/* Add / Edit Variant Modal */}
      <Dialog open={variantModalOpen} onOpenChange={setVariantModalOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingVariant ? `Edit Variant: ${editingVariant.sku}` : 'Add Variant'}</DialogTitle>
            <DialogDescription>
              SKU is normalized to uppercase and must be unique across your store.
            </DialogDescription>
          </DialogHeader>

          {vFormError && (
            <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700">
              {vFormError}
            </div>
          )}

          <form onSubmit={handleVariantSave} className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1">
                <Label htmlFor="modal-v-sku">SKU *</Label>
                <Input
                  id="modal-v-sku"
                  value={vSku}
                  onChange={(e) => setVSku(e.target.value)}
                  className="uppercase font-mono"
                  required
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="modal-v-name">Name *</Label>
                <Input
                  id="modal-v-name"
                  value={vName}
                  onChange={(e) => setVName(e.target.value)}
                  required
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="modal-v-cost">Cost Price</Label>
                <Input
                  id="modal-v-cost"
                  type="number"
                  step="0.0001"
                  min="0"
                  value={vCost}
                  onChange={(e) => setVCost(e.target.value)}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="modal-v-sell">Selling Price *</Label>
                <Input
                  id="modal-v-sell"
                  type="number"
                  step="0.0001"
                  min="0"
                  value={vSell}
                  onChange={(e) => setVSell(e.target.value)}
                  required
                />
              </div>
            </div>

            {product.productType === 'VARIABLE' &&
              product.variantOptions.map((opt) => (
                <div key={opt} className="space-y-1">
                  <Label htmlFor={`modal-v-opt-${opt}`}>{opt} *</Label>
                  <Input
                    id={`modal-v-opt-${opt}`}
                    value={vOptions[opt] ?? ''}
                    onChange={(e) => setVOptions((prev) => ({ ...prev, [opt]: e.target.value }))}
                    required
                  />
                </div>
              ))}

            {!editingVariant && (
              <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer">
                <input
                  type="checkbox"
                  checked={vIsDefault}
                  onChange={(e) => setVIsDefault(e.target.checked)}
                />
                <span>Make this the new default variant</span>
              </label>
            )}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setVariantModalOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={addVariantMutation.isPending || updateVariantMutation.isPending}
                className="bg-slate-900 hover:bg-slate-800"
              >
                {(addVariantMutation.isPending || updateVariantMutation.isPending) && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                Save Variant
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Add / Edit Barcode Modal */}
      <Dialog open={barcodeModalOpen} onOpenChange={setBarcodeModalOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingBarcode ? 'Edit Barcode' : 'Add Barcode'}</DialogTitle>
            <DialogDescription>
              Barcodes must be unique across your entire store catalog.
            </DialogDescription>
          </DialogHeader>

          {bFormError && (
            <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700">
              {bFormError}
            </div>
          )}

          <form onSubmit={handleBarcodeSave} className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="modal-b-val">Barcode Value *</Label>
              <Input
                id="modal-b-val"
                value={bValue}
                onChange={(e) => setBValue(e.target.value)}
                className="font-mono"
                required
              />
            </div>

            <div className="space-y-1">
              <Label htmlFor="modal-b-type">Barcode Type *</Label>
              <select
                id="modal-b-type"
                value={bType}
                onChange={(e) => setBType(e.target.value as BarcodeType)}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm"
              >
                {BARCODE_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            </div>

            <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer">
              <input
                type="checkbox"
                checked={bPrimary}
                onChange={(e) => setBPrimary(e.target.checked)}
              />
              <span>Set as primary barcode for this variant</span>
            </label>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setBarcodeModalOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={addBarcodeMutation.isPending || updateBarcodeMutation.isPending}
                className="bg-slate-900 hover:bg-slate-800"
              >
                {(addBarcodeMutation.isPending || updateBarcodeMutation.isPending) && (
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                )}
                Save Barcode
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Remove Barcode Confirmation Modal */}
      <Dialog
        open={!!deleteBarcodeTarget}
        onOpenChange={(o) => !o && setDeleteBarcodeTarget(null)}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Remove Barcode</DialogTitle>
            <DialogDescription>
              Are you sure you want to remove barcode{' '}
              <span className="font-mono font-semibold">{deleteBarcodeTarget?.barcode.barcode}</span>?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteBarcodeTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={removeBarcodeMutation.isPending}
              onClick={() => void handleConfirmRemoveBarcode()}
            >
              {removeBarcodeMutation.isPending && (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              )}
              Remove Barcode
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Convert SIMPLE to VARIABLE Modal */}
      <Dialog open={convertOpen} onOpenChange={setConvertOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Convert Product to VARIABLE</DialogTitle>
            <DialogDescription>
              Preserve the existing default variant while enabling option axes (e.g. Size, Color).
            </DialogDescription>
          </DialogHeader>

          {convertError && (
            <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700">
              {convertError}
            </div>
          )}

          <form onSubmit={handleConvertSubmit} className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="convert-axes">Option Names (comma-separated, max 3)</Label>
              <Input
                id="convert-axes"
                value={convertOptionsText}
                onChange={(e) => setConvertOptionsText(e.target.value)}
                placeholder="Size, Color"
              />
            </div>

            {parsedConvertAxes.map((axis) => (
              <div key={axis} className="space-y-1">
                <Label htmlFor={`convert-val-${axis}`}>
                  Value for existing variant ({axis}) *
                </Label>
                <Input
                  id={`convert-val-${axis}`}
                  value={convertDefaults[axis] ?? ''}
                  onChange={(e) =>
                    setConvertDefaults((prev) => ({ ...prev, [axis]: e.target.value }))
                  }
                  required
                />
              </div>
            ))}

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setConvertOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={convertMutation.isPending}
                className="bg-slate-900 hover:bg-slate-800"
              >
                {convertMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Convert Product
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </Card>
  );
};
