import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ProductForm } from './ProductForm';
import type { Category } from '@/entities/category';
import type { UnitOfMeasure } from '@/entities/unit';
import type { ProductDetail } from '@/entities/product';

const CATEGORIES: Category[] = [
  {
    id: 'cat-active',
    parentId: null,
    code: 'BEV',
    name: 'Beverages',
    description: null,
    depth: 0,
    sortOrder: 0,
    isActive: true,
    createdAt: '',
    updatedAt: '',
    version: 0,
  },
];

const UNITS: UnitOfMeasure[] = [
  {
    id: 'u-pcs',
    code: 'PCS',
    name: 'Piece',
    symbol: 'pcs',
    allowDecimal: false,
    decimalPrecision: 0,
    isSystem: true,
    isActive: true,
    createdAt: '',
    updatedAt: '',
    version: 0,
  },
];

describe('ProductForm validation, VARIABLE mode, and historical inactive editing', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('validates required SKU and option values on VARIABLE product submission', async () => {
    const onCreateSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ProductForm
        mode="create"
        categories={CATEGORIES}
        units={UNITS}
        isSubmitting={false}
        onCancel={vi.fn()}
        onCreateSubmit={onCreateSubmit}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: /VARIABLE Product/i }));
    fireEvent.change(screen.getByLabelText(/Product Name/i), { target: { value: 'Hoodie' } });
    fireEvent.change(screen.getByLabelText(/^SKU \*/i), { target: { value: 'HD-001' } });
    fireEvent.change(screen.getByLabelText(/Variant Name \*/i), { target: { value: 'Large' } });

    // Submit with missing "Size" option value
    fireEvent.click(screen.getByRole('button', { name: 'Create Product' }));

    await waitFor(() => {
      expect(
        screen.getByText(/Variant #1 must provide a value for option "Size"/i)
      ).toBeInTheDocument();
    });
    expect(onCreateSubmit).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Size \*/i), { target: { value: 'L' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create Product' }));

    await waitFor(() => {
      expect(onCreateSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Hoodie',
          productType: 'VARIABLE',
          variantOptions: ['Size'],
          variants: [
            expect.objectContaining({
              sku: 'HD-001',
              name: 'Large',
              optionValues: { Size: 'L' },
            }),
          ],
        })
      );
    });
  });

  it('preserves historical inactive category and UOM options in edit mode', async () => {
    const onEditSubmit = vi.fn().mockResolvedValue(undefined);
    const historicalProduct: ProductDetail = {
      id: 'prod-1',
      name: 'Legacy Item',
      description: 'Old stock',
      brand: 'OldBrand',
      productType: 'SIMPLE',
      variantOptions: [],
      status: 'ACTIVE',
      category: { id: 'cat-inactive', code: 'OLD', name: 'Legacy Category' },
      unit: {
        id: 'u-inactive',
        code: 'BOX_OLD',
        name: 'Old Box',
        symbol: 'box',
        allowDecimal: false,
        decimalPrecision: 0,
      },
      variants: [],
      activeDefaultVariantId: null,
      historicalDefaultVariantId: null,
      createdAt: '',
      updatedAt: '',
      version: 0,
    };

    render(
      <ProductForm
        mode="edit"
        initialProduct={historicalProduct}
        categories={CATEGORIES}
        units={UNITS}
        isSubmitting={false}
        onCancel={vi.fn()}
        onEditSubmit={onEditSubmit}
      />
    );

    expect(screen.getByText('Legacy Category [Inactive]')).toBeInTheDocument();
    expect(screen.getByText('Old Box [Inactive] (BOX_OLD / box)')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Save Product' }));

    await waitFor(() => {
      expect(onEditSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          categoryId: 'cat-inactive',
          unitId: 'u-inactive',
          name: 'Legacy Item',
        })
      );
    });
  });
});
