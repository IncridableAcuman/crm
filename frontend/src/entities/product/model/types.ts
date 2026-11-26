import type { CategorySummary } from '@/entities/category';
import type { UnitSummary } from '@/entities/unit';

export type ProductType = 'SIMPLE' | 'VARIABLE';
export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
export type BarcodeType = 'EAN13' | 'EAN8' | 'UPC_A' | 'CODE128' | 'INTERNAL' | 'OTHER';

export interface ProductBarcode {
  id: string;
  variantId: string;
  barcode: string;
  barcodeType: BarcodeType;
  isPrimary: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ProductVariant {
  id: string;
  productId: string;
  sku: string;
  name: string;
  optionValues: Record<string, string>;
  costPrice: number;
  sellingPrice: number;
  isDefault: boolean;
  isActiveDefault: boolean;
  isActive: boolean;
  barcodes: ProductBarcode[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ProductSummary {
  id: string;
  name: string;
  brand: string | null;
  productType: ProductType;
  status: ProductStatus;
  categoryId: string | null;
  categoryName: string | null;
  unitId: string;
  unitCode: string;
  unitSymbol: string;
  variantCount: number;
  defaultSku: string | null;
  primaryBarcode: string | null;
  minSellingPrice: number | null;
  maxSellingPrice: number | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ProductDetail {
  id: string;
  name: string;
  description: string | null;
  brand: string | null;
  productType: ProductType;
  variantOptions: string[];
  status: ProductStatus;
  category: CategorySummary | null;
  unit: UnitSummary;
  variants: ProductVariant[];
  activeDefaultVariantId: string | null;
  historicalDefaultVariantId: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface VariantLookup {
  variantId: string;
  productId: string;
  productName: string;
  variantName: string;
  sku: string;
  primaryBarcode: string | null;
  optionValues: Record<string, string>;
  unitId: string;
  unitCode: string;
  unitSymbol: string;
  allowDecimal: boolean;
  decimalPrecision: number;
  referenceCostPrice: number;
  sellingPrice: number;
  productStatus: ProductStatus;
  variantActive: boolean;
  activeDefault: boolean;
  sellable: boolean;
}

export interface ProductListParams {
  search?: string;
  sku?: string;
  barcode?: string;
  categoryId?: string;
  includeSubcategories?: boolean;
  unitId?: string;
  brand?: string;
  status?: ProductStatus;
  productType?: ProductType;
  isActive?: boolean;
  includeArchived?: boolean;
  branchId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface BarcodeInputRequest {
  barcode: string;
  barcodeType: BarcodeType;
  isPrimary: boolean;
}

export interface CreateVariantRequest {
  sku: string;
  name: string;
  optionValues?: Record<string, string>;
  costPrice?: number;
  sellingPrice?: number;
  isDefault: boolean;
  barcodes?: BarcodeInputRequest[];
}

export interface CreateProductRequest {
  categoryId?: string | null;
  unitId: string;
  name: string;
  description?: string | null;
  brand?: string | null;
  productType: ProductType;
  variantOptions?: string[];
  status?: ProductStatus;
  variants: CreateVariantRequest[];
}

export interface UpdateProductRequest {
  categoryId?: string | null;
  unitId: string;
  name: string;
  description?: string | null;
  brand?: string | null;
}

export interface ConvertProductToVariableRequest {
  variantOptions: string[];
  existingDefaultOptionValues: Record<string, string>;
}

export interface UpdateVariantRequest {
  sku: string;
  name: string;
  optionValues?: Record<string, string>;
  costPrice?: number;
  sellingPrice?: number;
}

export interface AddBarcodeRequest {
  barcode: string;
  barcodeType: BarcodeType;
  isPrimary: boolean;
}

export interface UpdateBarcodeRequest {
  barcode: string;
  barcodeType: BarcodeType;
  isPrimary: boolean;
}
