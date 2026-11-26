import React from 'react';
import { PageResponse } from '@/entities/session/model/types';
import { ProductSummary } from '@/entities/product';
import { PermissionGuard } from '@/entities/permission';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table';
import {
  Archive,
  ChevronLeft,
  ChevronRight,
  Edit3,
  Eye,
  RotateCcw,
} from 'lucide-react';

export interface ProductTableProps {
  pageData: PageResponse<ProductSummary>;
  pendingProductId?: string | null;
  onView: (productId: string) => void;
  onEdit: (productId: string) => void;
  onArchive: (product: ProductSummary) => void;
  onRestore: (product: ProductSummary) => void;
  onPageChange: (nextPage: number) => void;
}

function formatPriceRange(min: number | null, max: number | null): string {
  if (min === null && max === null) return '—';
  const minNum = Number(min ?? max ?? 0);
  const maxNum = Number(max ?? min ?? 0);
  if (minNum === maxNum) {
    return minNum.toFixed(2);
  }
  return `${minNum.toFixed(2)} – ${maxNum.toFixed(2)}`;
}

export const ProductTable: React.FC<ProductTableProps> = ({
  pageData,
  pendingProductId = null,
  onView,
  onEdit,
  onArchive,
  onRestore,
  onPageChange,
}) => {
  return (
    <div className="space-y-4">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Product</TableHead>
            <TableHead>Type</TableHead>
            <TableHead>Category</TableHead>
            <TableHead>Default SKU / Barcode</TableHead>
            <TableHead>Price</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {pageData.items.map((item) => (
            <TableRow key={item.id}>
              <TableCell>
                <div className="font-medium text-slate-900">{item.name}</div>
                <div className="text-xs text-slate-500">
                  {item.brand ? `${item.brand} · ` : ''}
                  {item.unitCode} ({item.unitSymbol})
                </div>
              </TableCell>
              <TableCell>
                <Badge variant="outline" className="text-[10px]">
                  {item.productType} ({item.variantCount})
                </Badge>
              </TableCell>
              <TableCell className="text-xs text-slate-600">
                {item.categoryName ?? <span className="text-slate-400">Uncategorized</span>}
              </TableCell>
              <TableCell>
                <div className="font-mono text-xs font-medium text-slate-800">
                  {item.defaultSku ?? '—'}
                </div>
                {item.primaryBarcode && (
                  <div className="font-mono text-[11px] text-slate-400">
                    {item.primaryBarcode}
                  </div>
                )}
              </TableCell>
              <TableCell className="font-mono text-xs font-medium text-slate-900">
                {formatPriceRange(item.minSellingPrice, item.maxSellingPrice)}
              </TableCell>
              <TableCell>
                <Badge
                  variant={
                    item.status === 'ACTIVE'
                      ? 'success'
                      : item.status === 'DRAFT'
                        ? 'warning'
                        : 'secondary'
                  }
                  className="text-[10px]"
                >
                  {item.status}
                </Badge>
              </TableCell>
              <TableCell className="text-right">
                <div className="flex items-center justify-end gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onView(item.id)}
                    aria-label={`View ${item.name}`}
                    className="h-7 px-2 text-xs"
                  >
                    <Eye className="h-3.5 w-3.5" />
                  </Button>

                  <PermissionGuard permission="catalog.products.update">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => onEdit(item.id)}
                      aria-label={`Edit ${item.name}`}
                      className="h-7 px-2 text-xs"
                    >
                      <Edit3 className="h-3.5 w-3.5" />
                    </Button>
                  </PermissionGuard>

                  {item.status !== 'ARCHIVED' ? (
                    <PermissionGuard permission="catalog.products.delete">
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={pendingProductId === item.id}
                        onClick={() => onArchive(item)}
                        aria-label={`Archive ${item.name}`}
                        className="h-7 px-2 text-xs text-amber-600 hover:text-amber-700"
                      >
                        <Archive className="h-3.5 w-3.5" />
                      </Button>
                    </PermissionGuard>
                  ) : (
                    <PermissionGuard permission="catalog.products.update">
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={pendingProductId === item.id}
                        onClick={() => onRestore(item)}
                        aria-label={`Restore ${item.name}`}
                        className="h-7 px-2 text-xs text-emerald-600 hover:text-emerald-700"
                      >
                        <RotateCcw className="h-3.5 w-3.5" />
                      </Button>
                    </PermissionGuard>
                  )}
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-t border-slate-100 pt-3 text-xs text-slate-500">
        <div>
          Showing page <span className="font-semibold text-slate-800">{pageData.page + 1}</span> of{' '}
          <span className="font-semibold text-slate-800">{Math.max(1, pageData.totalPages)}</span>{' '}
          ({pageData.totalElements} total products)
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={pageData.first}
            onClick={() => onPageChange(pageData.page - 1)}
            className="h-8 gap-1 text-xs"
          >
            <ChevronLeft className="h-3.5 w-3.5" />
            <span>Previous</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={pageData.last}
            onClick={() => onPageChange(pageData.page + 1)}
            className="h-8 gap-1 text-xs"
          >
            <span>Next</span>
            <ChevronRight className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>
    </div>
  );
};
