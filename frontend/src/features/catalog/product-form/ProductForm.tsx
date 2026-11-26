import React, { useEffect, useState } from 'react';
import { useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Category } from '@/entities/category';
import { UnitOfMeasure } from '@/entities/unit';
import {
  BarcodeType,
  CreateProductRequest,
  ProductDetail,
  ProductStatus,
  ProductType,
  UpdateProductRequest,
} from '@/entities/product';
import { parseApiError } from '@/shared/api/errorHandler';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Input } from '@/shared/ui/input';
import { Label } from '@/shared/ui/label';
import { AlertCircle, Loader2, Plus, Trash2 } from 'lucide-react';

const BARCODE_TYPES: BarcodeType[] = ['EAN13', 'EAN8', 'UPC_A', 'CODE128', 'INTERNAL', 'OTHER'];

const barcodeItemSchema = z.object({
  barcode: z
    .string()
    .trim()
    .min(1, 'Barcode value is required.')
    .max(100, 'Barcode cannot exceed 100 characters.'),
  barcodeType: z.enum(['EAN13', 'EAN8', 'UPC_A', 'CODE128', 'INTERNAL', 'OTHER']),
  isPrimary: z.boolean(),
});

const variantRowSchema = z.object({
  sku: z
    .string()
    .trim()
    .min(2, 'SKU must be 2 to 100 characters.')
    .max(100, 'SKU cannot exceed 100 characters.')
    .regex(/^[A-Za-z0-9._-]{2,100}$/, "SKU may only contain letters, digits, '.', '_', and '-'."),
  name: z
    .string()
    .trim()
    .min(1, 'Variant name is required.')
    .max(220, 'Variant name cannot exceed 220 characters.'),
  optionValues: z.record(z.string(), z.string()),
  costPrice: z.coerce.number().min(0, 'Cost price must be non-negative.'),
  sellingPrice: z.coerce.number().min(0, 'Selling price must be non-negative.'),
  isDefault: z.boolean(),
  barcodes: z.array(barcodeItemSchema),
});

const createProductFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Product name is required.')
    .max(200, 'Product name cannot exceed 200 characters.'),
  brand: z.string().max(100, 'Brand cannot exceed 100 characters.').optional(),
  categoryId: z.string().optional(),
  unitId: z.string().min(1, 'Unit of measure is required.'),
  description: z.string().max(4000, 'Description cannot exceed 4000 characters.').optional(),
  productType: z.enum(['SIMPLE', 'VARIABLE']),
  status: z.enum(['DRAFT', 'ACTIVE']),
  variantOptions: z.array(z.string().trim().min(1, 'Option name cannot be blank.').max(80)).max(3),
  variants: z.array(variantRowSchema).min(1, 'At least one variant is required.'),
});

const editProductFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Product name is required.')
    .max(200, 'Product name cannot exceed 200 characters.'),
  brand: z.string().max(100, 'Brand cannot exceed 100 characters.').optional(),
  categoryId: z.string().optional(),
  unitId: z.string().min(1, 'Unit of measure is required.'),
  description: z.string().max(4000, 'Description cannot exceed 4000 characters.').optional(),
});

type CreateProductFormValues = z.infer<typeof createProductFormSchema>;
type EditProductFormValues = z.infer<typeof editProductFormSchema>;

export interface ProductFormProps {
  mode: 'create' | 'edit';
  initialProduct?: ProductDetail;
  categories: Category[];
  units: UnitOfMeasure[];
  isSubmitting: boolean;
  onCancel: () => void;
  onCreateSubmit?: (payload: CreateProductRequest) => Promise<void>;
  onEditSubmit?: (payload: UpdateProductRequest) => Promise<void>;
}

export const ProductForm: React.FC<ProductFormProps> = ({
  mode,
  initialProduct,
  categories,
  units,
  isSubmitting,
  onCancel,
  onCreateSubmit,
  onEditSubmit,
}) => {
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [newOptionName, setNewOptionName] = useState('');

  const defaultUnitId = units.find((u) => u.code === 'PCS')?.id ?? units[0]?.id ?? '';

  const createForm = useForm<CreateProductFormValues>({
    resolver: zodResolver(createProductFormSchema),
    defaultValues: {
      name: '',
      brand: '',
      categoryId: '',
      unitId: defaultUnitId,
      description: '',
      productType: 'SIMPLE',
      status: 'ACTIVE',
      variantOptions: [],
      variants: [
        {
          sku: '',
          name: 'Default',
          optionValues: {},
          costPrice: 0,
          sellingPrice: 0,
          isDefault: true,
          barcodes: [],
        },
      ],
    },
  });

  const editForm = useForm<EditProductFormValues>({
    resolver: zodResolver(editProductFormSchema),
    defaultValues: {
      name: initialProduct?.name ?? '',
      brand: initialProduct?.brand ?? '',
      categoryId: initialProduct?.category?.id ?? '',
      unitId: initialProduct?.unit?.id ?? defaultUnitId,
      description: initialProduct?.description ?? '',
    },
  });

  useEffect(() => {
    if (mode === 'create' && defaultUnitId && !createForm.getValues('unitId')) {
      createForm.setValue('unitId', defaultUnitId);
    }
  }, [mode, defaultUnitId, createForm]);

  useEffect(() => {
    if (mode === 'edit' && initialProduct) {
      editForm.reset({
        name: initialProduct.name,
        brand: initialProduct.brand ?? '',
        categoryId: initialProduct.category?.id ?? '',
        unitId: initialProduct.unit.id,
        description: initialProduct.description ?? '',
      });
    }
  }, [mode, initialProduct, editForm]);

  const { fields: variantFields, append: appendVariant, remove: removeVariant } = useFieldArray({
    control: createForm.control,
    name: 'variants',
  });

  const productType = createForm.watch('productType');
  const variantOptions = createForm.watch('variantOptions');
  const selectedCreateUnitId = createForm.watch('unitId');
  const selectedEditUnitId = editForm.watch('unitId');

  const activeUnit = units.find(
    (u) => u.id === (mode === 'create' ? selectedCreateUnitId : selectedEditUnitId)
  );

  const handleProductTypeChange = (nextType: ProductType) => {
    createForm.setValue('productType', nextType);
    if (nextType === 'SIMPLE') {
      createForm.setValue('variantOptions', []);
      const first = createForm.getValues('variants.0');
      createForm.setValue('variants', [
        {
          sku: first?.sku ?? '',
          name: first?.name?.trim() || 'Default',
          optionValues: {},
          costPrice: first?.costPrice ?? 0,
          sellingPrice: first?.sellingPrice ?? 0,
          isDefault: true,
          barcodes: first?.barcodes ?? [],
        },
      ]);
    } else if (createForm.getValues('variantOptions').length === 0) {
      createForm.setValue('variantOptions', ['Size']);
    }
  };

  const addOptionKey = () => {
    const trimmed = newOptionName.trim();
    if (!trimmed) return;
    const current = createForm.getValues('variantOptions');
    if (current.length >= 3 || current.some((o) => o.toLowerCase() === trimmed.toLowerCase())) {
      return;
    }
    createForm.setValue('variantOptions', [...current, trimmed]);
    setNewOptionName('');
  };

  const removeOptionKey = (index: number) => {
    const current = createForm.getValues('variantOptions');
    const removedKey = current[index];
    const next = current.filter((_, i) => i !== index);
    createForm.setValue('variantOptions', next);
    if (removedKey) {
      const rows = createForm.getValues('variants');
      rows.forEach((row, idx) => {
        const copy = { ...row.optionValues };
        delete copy[removedKey];
        createForm.setValue(`variants.${idx}.optionValues`, copy);
      });
    }
  };

  const setDefaultVariantIndex = (targetIdx: number) => {
    const rows = createForm.getValues('variants');
    rows.forEach((_, idx) => {
      createForm.setValue(`variants.${idx}.isDefault`, idx === targetIdx);
    });
  };

  const addBarcodeToVariant = (variantIdx: number) => {
    const current = createForm.getValues(`variants.${variantIdx}.barcodes`) ?? [];
    createForm.setValue(`variants.${variantIdx}.barcodes`, [
      ...current,
      {
        barcode: '',
        barcodeType: 'EAN13',
        isPrimary: current.length === 0,
      },
    ]);
  };

  const removeBarcodeFromVariant = (variantIdx: number, barcodeIdx: number) => {
    const current = createForm.getValues(`variants.${variantIdx}.barcodes`) ?? [];
    const next = current.filter((_, i) => i !== barcodeIdx);
    if (next.length > 0 && !next.some((b) => b.isPrimary)) {
      const first = next[0];
      if (first) first.isPrimary = true;
    }
    createForm.setValue(`variants.${variantIdx}.barcodes`, next);
  };

  const setPrimaryBarcodeForVariant = (variantIdx: number, barcodeIdx: number) => {
    const current = createForm.getValues(`variants.${variantIdx}.barcodes`) ?? [];
    createForm.setValue(
      `variants.${variantIdx}.barcodes`,
      current.map((b, idx) => ({ ...b, isPrimary: idx === barcodeIdx }))
    );
  };

  const handleCreateSubmit = createForm.handleSubmit(async (values) => {
    if (!onCreateSubmit) return;
    setSubmitError(null);

    if (values.productType === 'VARIABLE') {
      if (values.variantOptions.length === 0) {
        setSubmitError('VARIABLE products require at least 1 variant option (e.g. Size).');
        return;
      }
      for (let i = 0; i < values.variants.length; i++) {
        const v = values.variants[i];
        if (!v) continue;
        for (const opt of values.variantOptions) {
          if (!v.optionValues[opt]?.trim()) {
            setSubmitError(`Variant #${i + 1} must provide a value for option "${opt}".`);
            return;
          }
        }
      }
    }

    const payload: CreateProductRequest = {
      name: values.name.trim(),
      brand: values.brand?.trim() || null,
      categoryId: values.categoryId ? values.categoryId : null,
      unitId: values.unitId,
      description: values.description?.trim() || null,
      productType: values.productType,
      variantOptions: values.productType === 'VARIABLE' ? values.variantOptions : [],
      status: values.status as ProductStatus,
      variants: values.variants.map((v, idx) => ({
        sku: v.sku.trim().toUpperCase(),
        name: v.name.trim(),
        optionValues:
          values.productType === 'VARIABLE'
            ? Object.fromEntries(
                values.variantOptions.map((opt) => [opt, (v.optionValues[opt] ?? '').trim()])
              )
            : {},
        costPrice: v.costPrice,
        sellingPrice: v.sellingPrice,
        isDefault: values.variants.some((x) => x.isDefault) ? v.isDefault : idx === 0,
        barcodes: v.barcodes.map((b) => ({
          barcode: b.barcode.trim(),
          barcodeType: b.barcodeType,
          isPrimary: b.isPrimary,
        })),
      })),
    };

    try {
      await onCreateSubmit(payload);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to create product.');
      for (const [field, message] of Object.entries(parsed.fieldErrors)) {
        if (
          field === 'name' ||
          field === 'brand' ||
          field === 'categoryId' ||
          field === 'unitId' ||
          field === 'description'
        ) {
          createForm.setError(field, { message });
        }
      }
      setSubmitError(parsed.message);
    }
  });

  const handleEditSubmit = editForm.handleSubmit(async (values) => {
    if (!onEditSubmit) return;
    setSubmitError(null);

    try {
      await onEditSubmit({
        name: values.name.trim(),
        brand: values.brand?.trim() || null,
        categoryId: values.categoryId ? values.categoryId : null,
        unitId: values.unitId,
        description: values.description?.trim() || null,
      });
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to update product.');
      for (const [field, message] of Object.entries(parsed.fieldErrors)) {
        if (
          field === 'name' ||
          field === 'brand' ||
          field === 'categoryId' ||
          field === 'unitId' ||
          field === 'description'
        ) {
          editForm.setError(field, { message });
        }
      }
      setSubmitError(parsed.message);
    }
  });

  const activeCategories = categories.filter((c) => c.isActive);
  const activeUnits = units.filter((u) => u.isActive);

  const editCategories =
    mode === 'edit' &&
    initialProduct?.category &&
    !activeCategories.some((c) => c.id === initialProduct.category?.id)
      ? [
          ...activeCategories,
          {
            id: initialProduct.category.id,
            parentId: null,
            code: initialProduct.category.code,
            name: `${initialProduct.category.name} [Inactive]`,
            description: null,
            depth: 0,
            sortOrder: 0,
            isActive: false,
            createdAt: '',
            updatedAt: '',
            version: 0,
          },
        ]
      : activeCategories;

  const editUnits =
    mode === 'edit' &&
    initialProduct?.unit &&
    !activeUnits.some((u) => u.id === initialProduct.unit.id)
      ? [
          ...activeUnits,
          {
            ...initialProduct.unit,
            name: `${initialProduct.unit.name} [Inactive]`,
            isSystem: false,
            isActive: false,
            createdAt: '',
            updatedAt: '',
            version: 0,
          },
        ]
      : activeUnits;

  if (mode === 'edit') {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Edit Product Metadata</CardTitle>
          <CardDescription>
            Update product title, brand, classification category, measurement unit, and description.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleEditSubmit} className="space-y-4">
            {submitError && (
              <div
                role="alert"
                className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700"
              >
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{submitError}</span>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label htmlFor="edit-prod-name">Product Name *</Label>
                <Input id="edit-prod-name" {...editForm.register('name')} />
                {editForm.formState.errors.name && (
                  <p className="text-xs text-red-600">{editForm.formState.errors.name.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-prod-brand">Brand</Label>
                <Input id="edit-prod-brand" {...editForm.register('brand')} />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-prod-category">Category</Label>
                <select
                  id="edit-prod-category"
                  className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm"
                  {...editForm.register('categoryId')}
                >
                  <option value="">Uncategorized</option>
                  {editCategories.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {'— '.repeat(cat.depth)}
                      {cat.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-prod-unit">Unit of Measure *</Label>
                <select
                  id="edit-prod-unit"
                  className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm"
                  {...editForm.register('unitId')}
                >
                  {editUnits.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name} ({u.code} / {u.symbol})
                    </option>
                  ))}
                </select>
                {editForm.formState.errors.unitId && (
                  <p className="text-xs text-red-600">{editForm.formState.errors.unitId.message}</p>
                )}
                {activeUnit && (
                  <p className="text-[11px] text-slate-500">
                    Quantity mode:{' '}
                    {activeUnit.allowDecimal
                      ? `Decimal (up to ${activeUnit.decimalPrecision} decimal places)`
                      : 'Integer only'}
                  </p>
                )}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="edit-prod-desc">Description</Label>
              <textarea
                id="edit-prod-desc"
                rows={3}
                className="flex w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm shadow-sm"
                {...editForm.register('description')}
              />
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={onCancel}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={isSubmitting}
                className="bg-slate-900 hover:bg-slate-800"
              >
                {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Save Product
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    );
  }

  return (
    <form onSubmit={handleCreateSubmit} className="space-y-6">
      {submitError && (
        <div
          role="alert"
          className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700"
        >
          <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
          <span>{submitError}</span>
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">1. Product Identity & Classification</CardTitle>
          <CardDescription>
            Choose SIMPLE for single-SKU items or VARIABLE for multi-option matrices (Size, Color, Storage).
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap gap-3">
            <Button
              type="button"
              variant={productType === 'SIMPLE' ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleProductTypeChange('SIMPLE')}
            >
              SIMPLE Product (1 SKU)
            </Button>
            <Button
              type="button"
              variant={productType === 'VARIABLE' ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleProductTypeChange('VARIABLE')}
            >
              VARIABLE Product (Option Matrix)
            </Button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="prod-name">Product Name *</Label>
              <Input id="prod-name" placeholder="Cotton T-Shirt" {...createForm.register('name')} />
              {createForm.formState.errors.name && (
                <p className="text-xs text-red-600">{createForm.formState.errors.name.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="prod-brand">Brand</Label>
              <Input id="prod-brand" placeholder="BasicCo" {...createForm.register('brand')} />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="prod-category">Category</Label>
              <select
                id="prod-category"
                className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm"
                {...createForm.register('categoryId')}
              >
                <option value="">Uncategorized</option>
                {activeCategories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {'— '.repeat(cat.depth)}
                    {cat.name}
                  </option>
                ))}
              </select>
            </div>

              <div className="space-y-1.5">
                <Label htmlFor="prod-unit">Unit of Measure *</Label>
                <select
                  id="prod-unit"
                  className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm"
                  {...createForm.register('unitId')}
                >
                  {activeUnits.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name} ({u.code} / {u.symbol})
                    </option>
                  ))}
                </select>
                {createForm.formState.errors.unitId && (
                  <p className="text-xs text-red-600">{createForm.formState.errors.unitId.message}</p>
                )}
                {activeUnit && (
                <p className="text-[11px] text-slate-500">
                  {activeUnit.allowDecimal
                    ? `Allows fractional quantities (${activeUnit.decimalPrecision} decimal places).`
                    : 'Whole-number quantities only.'}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="prod-status">Initial Status</Label>
              <select
                id="prod-status"
                className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm"
                {...createForm.register('status')}
              >
                <option value="ACTIVE">ACTIVE (Sellable immediately)</option>
                <option value="DRAFT">DRAFT (Hidden from POS, can be deleted)</option>
              </select>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="prod-desc">Description</Label>
            <textarea
              id="prod-desc"
              rows={2}
              className="flex w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm shadow-sm"
              placeholder="Optional product specification"
              {...createForm.register('description')}
            />
          </div>
        </CardContent>
      </Card>

      {productType === 'VARIABLE' && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">2. Variant Options (Max 3 Axes)</CardTitle>
            <CardDescription>
              Define the attribute axes shared by every variant of this product (e.g., Size, Color).
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              {variantOptions.map((opt, idx) => (
                <Badge key={opt} variant="secondary" className="gap-1.5 py-1 px-2.5 text-xs">
                  <span>{opt}</span>
                  <button
                    type="button"
                    onClick={() => removeOptionKey(idx)}
                    aria-label={`Remove option ${opt}`}
                    className="text-slate-500 hover:text-red-600"
                  >
                    ×
                  </button>
                </Badge>
              ))}
            </div>

            {variantOptions.length < 3 && (
              <div className="flex flex-col gap-2 sm:max-w-sm sm:flex-row">
                <Input
                  placeholder="Option name (e.g. Color)"
                  value={newOptionName}
                  onChange={(e) => setNewOptionName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addOptionKey();
                    }
                  }}
                />
                <Button type="button" variant="outline" size="sm" onClick={addOptionKey}>
                  Add Option
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle className="text-base">
              {productType === 'SIMPLE' ? '2. SKU, Pricing & Barcodes' : '3. Variants & Barcodes'}
            </CardTitle>
            <CardDescription>
              Selling price is the catalog list price; cost price is a reference purchase estimate only.
            </CardDescription>
          </div>
          {productType === 'VARIABLE' && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() =>
                appendVariant({
                  sku: '',
                  name: '',
                  optionValues: {},
                  costPrice: 0,
                  sellingPrice: 0,
                  isDefault: false,
                  barcodes: [],
                })
              }
              className="gap-1.5"
            >
              <Plus className="h-4 w-4" />
              <span>Add Variant Row</span>
            </Button>
          )}
        </CardHeader>

        <CardContent className="space-y-4">
          {variantFields.map((field, vIdx) => {
            const barcodes = createForm.watch(`variants.${vIdx}.barcodes`) ?? [];
            const isDefault = createForm.watch(`variants.${vIdx}.isDefault`);

            return (
              <div
                key={field.id}
                className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 space-y-4"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className="text-xs font-semibold text-slate-700">
                      {productType === 'SIMPLE' ? 'Default SKU' : `Variant #${vIdx + 1}`}
                    </span>
                    {productType === 'VARIABLE' && (
                      <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
                        <input
                          type="radio"
                          name="defaultVariant"
                          checked={isDefault}
                          onChange={() => setDefaultVariantIndex(vIdx)}
                        />
                        <span>Default variant</span>
                      </label>
                    )}
                  </div>

                  {productType === 'VARIABLE' && variantFields.length > 1 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      aria-label={`Remove variant ${vIdx + 1}`}
                      onClick={() => removeVariant(vIdx)}
                      className="text-red-600 hover:text-red-700 h-7 px-2"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                  <div className="space-y-1">
                    <Label htmlFor={`v-sku-${vIdx}`}>SKU *</Label>
                    <Input
                      id={`v-sku-${vIdx}`}
                      placeholder="TEE-001"
                      className="uppercase font-mono"
                      {...createForm.register(`variants.${vIdx}.sku`)}
                    />
                    {createForm.formState.errors.variants?.[vIdx]?.sku && (
                      <p className="text-xs text-red-600">
                        {createForm.formState.errors.variants[vIdx]?.sku?.message}
                      </p>
                    )}
                  </div>

                  <div className="space-y-1">
                    <Label htmlFor={`v-name-${vIdx}`}>Variant Name *</Label>
                    <Input
                      id={`v-name-${vIdx}`}
                      placeholder="Default"
                      {...createForm.register(`variants.${vIdx}.name`)}
                    />
                    {createForm.formState.errors.variants?.[vIdx]?.name && (
                      <p className="text-xs text-red-600">
                        {createForm.formState.errors.variants[vIdx]?.name?.message}
                      </p>
                    )}
                  </div>

                  <div className="space-y-1">
                    <Label htmlFor={`v-cost-${vIdx}`}>Reference Cost Price</Label>
                    <Input
                      id={`v-cost-${vIdx}`}
                      type="number"
                      step="0.0001"
                      min="0"
                      {...createForm.register(`variants.${vIdx}.costPrice`)}
                    />
                    {createForm.formState.errors.variants?.[vIdx]?.costPrice && (
                      <p className="text-xs text-red-600">
                        {createForm.formState.errors.variants[vIdx]?.costPrice?.message}
                      </p>
                    )}
                  </div>

                  <div className="space-y-1">
                    <Label htmlFor={`v-sell-${vIdx}`}>Selling Price *</Label>
                    <Input
                      id={`v-sell-${vIdx}`}
                      type="number"
                      step="0.0001"
                      min="0"
                      {...createForm.register(`variants.${vIdx}.sellingPrice`)}
                    />
                    {createForm.formState.errors.variants?.[vIdx]?.sellingPrice && (
                      <p className="text-xs text-red-600">
                        {createForm.formState.errors.variants[vIdx]?.sellingPrice?.message}
                      </p>
                    )}
                  </div>
                </div>

                {productType === 'VARIABLE' && variantOptions.length > 0 && (
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    {variantOptions.map((opt) => (
                      <div key={opt} className="space-y-1">
                        <Label htmlFor={`v-${vIdx}-opt-${opt}`}>{opt} *</Label>
                        <Input
                          id={`v-${vIdx}-opt-${opt}`}
                          placeholder={`Enter ${opt}`}
                          {...createForm.register(`variants.${vIdx}.optionValues.${opt}`)}
                        />
                      </div>
                    ))}
                  </div>
                )}

                <div className="space-y-2 border-t border-slate-200 pt-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-slate-600">
                      Barcodes ({barcodes.length})
                    </span>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => addBarcodeToVariant(vIdx)}
                      className="h-7 text-xs gap-1"
                    >
                      <Plus className="h-3 w-3" />
                      <span>Add Barcode</span>
                    </Button>
                  </div>

                  {barcodes.map((b, bIdx) => (
                    <div key={bIdx} className="space-y-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <Input
                          aria-label={`Variant ${vIdx + 1} barcode ${bIdx + 1} value`}
                          placeholder="4601000000011"
                          className="font-mono text-xs flex-1 min-w-[160px]"
                          {...createForm.register(`variants.${vIdx}.barcodes.${bIdx}.barcode`)}
                        />
                        <select
                          aria-label={`Variant ${vIdx + 1} barcode ${bIdx + 1} type`}
                          className="h-9 rounded-md border border-slate-200 bg-white px-2 text-xs"
                          {...createForm.register(`variants.${vIdx}.barcodes.${bIdx}.barcodeType`)}
                        >
                          {BARCODE_TYPES.map((t) => (
                            <option key={t} value={t}>
                              {t}
                            </option>
                          ))}
                        </select>
                        <label className="flex items-center gap-1 text-xs text-slate-600 cursor-pointer">
                          <input
                            type="radio"
                            name={`primaryBarcode-${vIdx}`}
                            checked={b.isPrimary}
                            onChange={() => setPrimaryBarcodeForVariant(vIdx, bIdx)}
                          />
                          <span>Primary</span>
                        </label>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          aria-label={`Remove variant ${vIdx + 1} barcode ${bIdx + 1}`}
                          onClick={() => removeBarcodeFromVariant(vIdx, bIdx)}
                          className="h-8 px-2 text-red-600"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                      {createForm.formState.errors.variants?.[vIdx]?.barcodes?.[bIdx]?.barcode && (
                        <p className="text-xs text-red-600">
                          {createForm.formState.errors.variants[vIdx]?.barcodes?.[bIdx]?.barcode?.message}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>

      <div className="flex items-center justify-end gap-2">
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button
          type="submit"
          disabled={isSubmitting}
          className="bg-slate-900 hover:bg-slate-800"
        >
          {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          Create Product
        </Button>
      </div>
    </form>
  );
};
