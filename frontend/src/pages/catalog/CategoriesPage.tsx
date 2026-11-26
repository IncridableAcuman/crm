import React, { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Category,
  useCategories,
  useCategoryTree,
  useDeleteCategory,
  useUpdateCategoryStatus,
} from '@/entities/category';
import { useBranches } from '@/entities/branch';
import {
  PermissionGuard,
  useCatalogReadBranchScope,
  useHasAnyScopePermission,
} from '@/entities/permission';
import { CategoryTree } from '@/widgets/category-tree/CategoryTree';
import { CategoryFormDialog } from '@/features/catalog/category-form/CategoryFormDialog';
import { UnitManagerPanel } from '@/features/catalog/unit-manager/UnitManagerPanel';
import { parseApiError } from '@/shared/api/errorHandler';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
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
  FolderTree,
  Loader2,
  Plus,
  RefreshCw,
  Ruler,
} from 'lucide-react';

export const CategoriesPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const branchIdParam = searchParams.get('branchId') ?? undefined;

  const [activeTab, setActiveTab] = useState<'categories' | 'units'>('categories');
  const [includeInactive, setIncludeInactive] = useState(false);

  const categoryScope = useCatalogReadBranchScope('catalog.categories.read', branchIdParam);
  const canReadBranches = useHasAnyScopePermission('tenancy.branches.read');
  const { data: branches = [] } = useBranches({ enabled: canReadBranches });

  const {
    data: treeNodes = [],
    isLoading: treeLoading,
    isError: treeError,
    error: treeErrorObj,
    refetch: refetchTree,
  } = useCategoryTree({
    includeInactive,
    branchId: categoryScope.resolvedBranchId,
    enabled: categoryScope.canRead,
  });

  const { data: allCategories = [] } = useCategories({
    includeInactive: true,
    branchId: categoryScope.resolvedBranchId,
    enabled: categoryScope.canRead,
  });

  const statusMutation = useUpdateCategoryStatus();
  const deleteMutation = useDeleteCategory();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<'create' | 'edit' | 'move'>('create');
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);
  const [defaultParentId, setDefaultParentId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);
  const [pendingCategoryId, setPendingCategoryId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const updateBranchParam = (nextBranchId: string | null) => {
    const next = new URLSearchParams(searchParams);
    if (nextBranchId) {
      next.set('branchId', nextBranchId);
    } else {
      next.delete('branchId');
    }
    setSearchParams(next);
  };

  if (categoryScope.invalidBranchRequested) {
    return (
      <Card className="mx-auto mt-8 max-w-lg border-amber-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base text-slate-900">
            <AlertCircle className="h-5 w-5 text-amber-600" />
            <span>Unauthorized Branch Context</span>
          </CardTitle>
          <CardDescription>
            You do not have permission to view categories for the requested branch.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="outline" size="sm" onClick={() => updateBranchParam(null)}>
            Reset to Authorized Branch
          </Button>
        </CardContent>
      </Card>
    );
  }

  const openCreateRoot = () => {
    setSelectedCategory(null);
    setDefaultParentId(null);
    setDialogMode('create');
    setDialogOpen(true);
  };

  const openAddChild = (parent: Category) => {
    setSelectedCategory(null);
    setDefaultParentId(parent.id);
    setDialogMode('create');
    setDialogOpen(true);
  };

  const openEdit = (category: Category) => {
    setSelectedCategory(category);
    setDefaultParentId(null);
    setDialogMode('edit');
    setDialogOpen(true);
  };

  const openMove = (category: Category) => {
    setSelectedCategory(category);
    setDefaultParentId(null);
    setDialogMode('move');
    setDialogOpen(true);
  };

  const handleToggleStatus = async (category: Category) => {
    if (statusMutation.isPending) return;
    setActionError(null);
    setPendingCategoryId(category.id);
    try {
      await statusMutation.mutateAsync({
        categoryId: category.id,
        isActive: !category.isActive,
      });
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to update category status.').message);
    } finally {
      setPendingCategoryId(null);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setActionError(null);
    try {
      await deleteMutation.mutateAsync(deleteTarget.id);
      setDeleteTarget(null);
    } catch (err) {
      setActionError(parseApiError(err, 'Failed to delete category.').message);
      setDeleteTarget(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <FolderTree className="h-6 w-6 text-slate-900" />
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              Categories & Units of Measure
            </h1>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Organize your product category hierarchy (up to 5 levels) and manage measurement units.
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {!categoryScope.isTenantScoped && categoryScope.authorizedBranchIds.length > 1 && (
            <select
              aria-label="Branch read context"
              value={categoryScope.resolvedBranchId ?? ''}
              onChange={(e) => updateBranchParam(e.target.value || null)}
              className="h-9 rounded-md border border-slate-200 bg-white px-3 text-xs shadow-sm"
            >
              {categoryScope.authorizedBranchIds.map((bId) => {
                const branch = branches.find((b) => b.id === bId);
                return (
                  <option key={bId} value={bId}>
                    {branch ? `${branch.name} (${branch.code})` : bId}
                  </option>
                );
              })}
            </select>
          )}
          <Button
            type="button"
            variant={activeTab === 'categories' ? 'default' : 'outline'}
            size="sm"
            onClick={() => setActiveTab('categories')}
            className="gap-1.5 text-xs"
          >
            <FolderTree className="h-3.5 w-3.5" />
            <span>Categories</span>
          </Button>
          <Button
            type="button"
            variant={activeTab === 'units' ? 'default' : 'outline'}
            size="sm"
            onClick={() => setActiveTab('units')}
            className="gap-1.5 text-xs"
          >
            <Ruler className="h-3.5 w-3.5" />
            <span>Units of Measure</span>
          </Button>
        </div>
      </div>

      {activeTab === 'categories' ? (
        <Card>
          <CardHeader className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <CardTitle className="text-base">Category Hierarchy</CardTitle>
              <CardDescription className="text-xs">
                Structured tree loaded in a single query. Leaf unused categories can be deleted.
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
                <Button
                  size="sm"
                  onClick={openCreateRoot}
                  className="gap-1.5 bg-slate-900 hover:bg-slate-800"
                >
                  <Plus className="h-4 w-4" />
                  <span>New Category</span>
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

            {treeLoading ? (
              <div
                role="status"
                className="flex h-36 items-center justify-center gap-2 text-sm text-slate-500"
              >
                <Loader2 className="h-5 w-5 animate-spin" />
                <span>Loading category tree...</span>
              </div>
            ) : treeError ? (
              <div
                role="alert"
                className="rounded-lg border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700 space-y-3"
              >
                <div>{parseApiError(treeErrorObj, 'Failed to load categories.').message}</div>
                <Button variant="outline" size="sm" onClick={() => void refetchTree()} className="gap-1.5">
                  <RefreshCw className="h-3.5 w-3.5" />
                  <span>Retry</span>
                </Button>
              </div>
            ) : treeNodes.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-10 text-center text-slate-500 space-y-3">
                <FolderTree className="h-10 w-10 text-slate-300" />
                <div>
                  <p className="font-medium text-slate-800">No product categories yet</p>
                  <p className="text-xs text-slate-400 mt-1 max-w-sm">
                    Create root categories and nested subcategories to classify your catalog items.
                  </p>
                </div>
                <PermissionGuard permission="catalog.categories.manage">
                  <Button
                    size="sm"
                    onClick={openCreateRoot}
                    className="gap-1.5 bg-slate-900 hover:bg-slate-800"
                  >
                    <Plus className="h-4 w-4" />
                    <span>Create First Category</span>
                  </Button>
                </PermissionGuard>
              </div>
            ) : (
              <CategoryTree
                nodes={treeNodes}
                pendingCategoryId={pendingCategoryId}
                onAddChild={openAddChild}
                onEdit={openEdit}
                onMove={openMove}
                onToggleStatus={(cat) => void handleToggleStatus(cat)}
                onDelete={(cat) => setDeleteTarget(cat)}
              />
            )}
          </CardContent>
        </Card>
      ) : (
        <UnitManagerPanel branchId={branchIdParam} />
      )}

      <CategoryFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        mode={dialogMode}
        categories={allCategories}
        category={selectedCategory}
        defaultParentId={defaultParentId}
      />

      {/* Delete Category Confirmation */}
      <Dialog open={!!deleteTarget} onOpenChange={(o) => !o && setDeleteTarget(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete Category</DialogTitle>
            <DialogDescription>
              Are you sure you want to delete{' '}
              <span className="font-semibold">{deleteTarget?.name}</span> ({deleteTarget?.code})?
              Categories with subcategories or assigned products cannot be deleted.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => void handleConfirmDelete()}
            >
              {deleteMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Delete Category
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};
