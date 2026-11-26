import React, { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Category,
  useCreateCategory,
  useMoveCategory,
  useUpdateCategory,
} from '@/entities/category';
import { parseApiError } from '@/shared/api/errorHandler';
import { Button } from '@/shared/ui/button';
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
import { AlertCircle, Loader2 } from 'lucide-react';

const categoryCreateSchema = z.object({
  parentId: z.string().optional(),
  code: z
    .string()
    .trim()
    .min(2, 'Code must be 2 to 50 characters.')
    .max(50, 'Code must be 2 to 50 characters.')
    .regex(/^[A-Za-z0-9_-]{2,50}$/, "Code may only contain letters, digits, '_', and '-'."),
  name: z
    .string()
    .trim()
    .min(1, 'Category name is required.')
    .max(120, 'Category name cannot exceed 120 characters.'),
  description: z.string().max(4000, 'Description cannot exceed 4000 characters.').optional(),
  sortOrder: z.coerce
    .number()
    .int('Sort order must be an integer.')
    .min(0, 'Sort order cannot be negative.'),
});

const categoryEditSchema = categoryCreateSchema.omit({ code: true, parentId: true });

type CategoryCreateFormValues = z.infer<typeof categoryCreateSchema>;
type CategoryEditFormValues = z.infer<typeof categoryEditSchema>;

function collectDescendantIds(rootId: string, categories: Category[]): Set<string> {
  const childrenByParent = new Map<string, string[]>();
  for (const cat of categories) {
    if (cat.parentId) {
      const list = childrenByParent.get(cat.parentId) ?? [];
      list.push(cat.id);
      childrenByParent.set(cat.parentId, list);
    }
  }
  const result = new Set<string>();
  const stack = [rootId];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current || result.has(current)) continue;
    result.add(current);
    const children = childrenByParent.get(current) ?? [];
    for (const childId of children) {
      stack.push(childId);
    }
  }
  return result;
}

function getSubtreeHeight(rootId: string, categories: Category[]): number {
  const childrenByParent = new Map<string, string[]>();
  for (const cat of categories) {
    if (cat.parentId) {
      const list = childrenByParent.get(cat.parentId) ?? [];
      list.push(cat.id);
      childrenByParent.set(cat.parentId, list);
    }
  }
  const measure = (id: string): number => {
    const children = childrenByParent.get(id) ?? [];
    if (children.length === 0) return 0;
    return 1 + Math.max(...children.map(measure));
  };
  return measure(rootId);
}

export interface CategoryFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: 'create' | 'edit' | 'move';
  categories: Category[];
  category?: Category | null;
  defaultParentId?: string | null;
}

export const CategoryFormDialog: React.FC<CategoryFormDialogProps> = ({
  open,
  onOpenChange,
  mode,
  categories,
  category,
  defaultParentId = null,
}) => {
  const createMutation = useCreateCategory();
  const updateMutation = useUpdateCategory();
  const moveMutation = useMoveCategory();

  const [submitError, setSubmitError] = useState<string | null>(null);
  const [moveParentId, setMoveParentId] = useState<string>('');

  const createForm = useForm<CategoryCreateFormValues>({
    resolver: zodResolver(categoryCreateSchema),
    defaultValues: {
      parentId: defaultParentId ?? '',
      code: '',
      name: '',
      description: '',
      sortOrder: 0,
    },
  });

  const editForm = useForm<CategoryEditFormValues>({
    resolver: zodResolver(categoryEditSchema),
    defaultValues: {
      name: '',
      description: '',
      sortOrder: 0,
    },
  });

  useEffect(() => {
    if (!open) {
      setSubmitError(null);
      return;
    }
    setSubmitError(null);
    if (mode === 'create') {
      createForm.reset({
        parentId: defaultParentId ?? '',
        code: '',
        name: '',
        description: '',
        sortOrder: 0,
      });
    } else if (mode === 'edit' && category) {
      editForm.reset({
        name: category.name,
        description: category.description ?? '',
        sortOrder: category.sortOrder,
      });
    } else if (mode === 'move' && category) {
      setMoveParentId(category.parentId ?? '');
    }
  }, [open, mode, category, defaultParentId, createForm, editForm]);

  const validParentOptions = useMemo(() => {
    if (mode === 'create') {
      return categories.filter((c) => c.isActive && c.depth < 4);
    }
    if (mode === 'move' && category) {
      const forbidden = collectDescendantIds(category.id, categories);
      const subtreeHeight = getSubtreeHeight(category.id, categories);
      return categories.filter(
        (c) => c.isActive && !forbidden.has(c.id) && c.depth + 1 + subtreeHeight <= 4
      );
    }
    return [];
  }, [mode, categories, category]);

  const handleCreateSubmit = createForm.handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await createMutation.mutateAsync({
        parentId: values.parentId ? values.parentId : null,
        code: values.code.trim().toUpperCase(),
        name: values.name.trim(),
        description: values.description?.trim() || null,
        sortOrder: values.sortOrder,
      });
      onOpenChange(false);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to create category.');
      for (const [field, message] of Object.entries(parsed.fieldErrors)) {
        if (field === 'code' || field === 'name' || field === 'description' || field === 'sortOrder' || field === 'parentId') {
          createForm.setError(field, { message });
        }
      }
      setSubmitError(parsed.message);
    }
  });

  const handleEditSubmit = editForm.handleSubmit(async (values) => {
    if (!category) return;
    setSubmitError(null);
    try {
      await updateMutation.mutateAsync({
        categoryId: category.id,
        data: {
          name: values.name.trim(),
          description: values.description?.trim() || null,
          sortOrder: values.sortOrder,
        },
      });
      onOpenChange(false);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to update category.');
      for (const [field, message] of Object.entries(parsed.fieldErrors)) {
        if (field === 'name' || field === 'description' || field === 'sortOrder') {
          editForm.setError(field, { message });
        }
      }
      setSubmitError(parsed.message);
    }
  });

  const handleMoveSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!category) return;
    setSubmitError(null);
    try {
      await moveMutation.mutateAsync({
        categoryId: category.id,
        data: {
          newParentId: moveParentId ? moveParentId : null,
        },
      });
      onOpenChange(false);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to move category.');
      setSubmitError(parsed.message);
    }
  };

  const isPending =
    createMutation.isPending || updateMutation.isPending || moveMutation.isPending;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>
            {mode === 'create'
              ? 'Create Category'
              : mode === 'edit'
                ? `Edit Category: ${category?.code ?? ''}`
                : `Move Category: ${category?.name ?? ''}`}
          </DialogTitle>
          <DialogDescription>
            {mode === 'create'
              ? 'Add a root or nested product category (maximum hierarchy depth is 5 levels).'
              : mode === 'edit'
                ? 'Update category display name, description, and sort order. Code cannot be changed.'
                : 'Select a new parent category or move to root level. Circular references are blocked.'}
          </DialogDescription>
        </DialogHeader>

        {submitError && (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 text-xs text-red-700"
          >
            <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
            <span>{submitError}</span>
          </div>
        )}

        {mode === 'create' && (
          <form onSubmit={handleCreateSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="cat-parent">Parent Category</Label>
              <select
                id="cat-parent"
                className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-slate-900"
                {...createForm.register('parentId')}
              >
                <option value="">Root (Top Level)</option>
                {validParentOptions.map((opt) => (
                  <option key={opt.id} value={opt.id}>
                    {'— '.repeat(opt.depth)}
                    {opt.name} ({opt.code})
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="cat-code">Code *</Label>
                <Input
                  id="cat-code"
                  placeholder="BEVERAGES"
                  className="uppercase font-mono"
                  {...createForm.register('code')}
                />
                {createForm.formState.errors.code && (
                  <p className="text-xs text-red-600">{createForm.formState.errors.code.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="cat-sort">Sort Order</Label>
                <Input
                  id="cat-sort"
                  type="number"
                  min={0}
                  {...createForm.register('sortOrder')}
                />
                {createForm.formState.errors.sortOrder && (
                  <p className="text-xs text-red-600">
                    {createForm.formState.errors.sortOrder.message}
                  </p>
                )}
              </div>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="cat-name">Name *</Label>
              <Input id="cat-name" placeholder="Cold Beverages" {...createForm.register('name')} />
              {createForm.formState.errors.name && (
                <p className="text-xs text-red-600">{createForm.formState.errors.name.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="cat-desc">Description</Label>
              <textarea
                id="cat-desc"
                rows={2}
                className="flex w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-slate-900"
                placeholder="Optional notes for this category"
                {...createForm.register('description')}
              />
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isPending} className="bg-slate-900 hover:bg-slate-800">
                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Create Category
              </Button>
            </DialogFooter>
          </form>
        )}

        {mode === 'edit' && (
          <form onSubmit={handleEditSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="edit-cat-name">Name *</Label>
              <Input id="edit-cat-name" {...editForm.register('name')} />
              {editForm.formState.errors.name && (
                <p className="text-xs text-red-600">{editForm.formState.errors.name.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="edit-cat-sort">Sort Order</Label>
              <Input id="edit-cat-sort" type="number" min={0} {...editForm.register('sortOrder')} />
              {editForm.formState.errors.sortOrder && (
                <p className="text-xs text-red-600">
                  {editForm.formState.errors.sortOrder.message}
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="edit-cat-desc">Description</Label>
              <textarea
                id="edit-cat-desc"
                rows={2}
                className="flex w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-slate-900"
                {...editForm.register('description')}
              />
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isPending} className="bg-slate-900 hover:bg-slate-800">
                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Save Changes
              </Button>
            </DialogFooter>
          </form>
        )}

        {mode === 'move' && (
          <form onSubmit={handleMoveSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="move-cat-parent">New Parent Category</Label>
              <select
                id="move-cat-parent"
                value={moveParentId}
                onChange={(e) => setMoveParentId(e.target.value)}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-white px-3 py-1 text-sm shadow-sm focus:outline-none focus:ring-1 focus:ring-slate-900"
              >
                <option value="">Root (Top Level)</option>
                {validParentOptions.map((opt) => (
                  <option key={opt.id} value={opt.id}>
                    {'— '.repeat(opt.depth)}
                    {opt.name} ({opt.code}) {!opt.isActive ? '[Inactive]' : ''}
                  </option>
                ))}
              </select>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={isPending} className="bg-slate-900 hover:bg-slate-800">
                {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                Move Category
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
};
