import React, { useState } from 'react';
import { Category, CategoryTreeNode } from '@/entities/category';
import { PermissionGuard } from '@/entities/permission';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import {
  ArrowRightLeft,
  ChevronDown,
  ChevronRight,
  Edit3,
  Folder,
  FolderPlus,
  Power,
  Trash2,
} from 'lucide-react';

export interface CategoryTreeProps {
  nodes: CategoryTreeNode[];
  pendingCategoryId?: string | null;
  onAddChild: (parentCategory: Category) => void;
  onEdit: (category: Category) => void;
  onMove: (category: Category) => void;
  onToggleStatus: (category: Category) => void;
  onDelete: (category: Category) => void;
}

function toCategory(node: CategoryTreeNode): Category {
  return {
    id: node.id,
    parentId: node.parentId,
    code: node.code,
    name: node.name,
    description: node.description,
    depth: node.depth,
    sortOrder: node.sortOrder,
    isActive: node.isActive,
    createdAt: node.createdAt,
    updatedAt: node.updatedAt,
    version: node.version,
  };
}

function collectAllIds(nodes: CategoryTreeNode[]): string[] {
  const ids: string[] = [];
  const walk = (list: CategoryTreeNode[]) => {
    for (const n of list) {
      ids.push(n.id);
      if (n.children.length > 0) walk(n.children);
    }
  };
  walk(nodes);
  return ids;
}

export const CategoryTree: React.FC<CategoryTreeProps> = ({
  nodes,
  pendingCategoryId = null,
  onAddChild,
  onEdit,
  onMove,
  onToggleStatus,
  onDelete,
}) => {
  const [collapsedIds, setCollapsedIds] = useState<Record<string, boolean>>({});

  const toggleNode = (id: string) => {
    setCollapsedIds((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const expandAll = () => setCollapsedIds({});
  const collapseAll = () => {
    const next: Record<string, boolean> = {};
    for (const id of collectAllIds(nodes)) {
      next[id] = true;
    }
    setCollapsedIds(next);
  };

  const renderNode = (node: CategoryTreeNode) => {
    const hasChildren = node.children.length > 0;
    const isExpanded = !collapsedIds[node.id];
    const cat = toCategory(node);

    return (
      <div key={node.id} className="space-y-1">
        <div
          className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2.5 hover:border-slate-300"
          style={{ marginLeft: `${node.depth * 20}px` }}
        >
          <div className="flex items-center gap-2 min-w-0">
            {hasChildren ? (
              <button
                type="button"
                onClick={() => toggleNode(node.id)}
                aria-label={`${isExpanded ? 'Collapse' : 'Expand'} ${node.name}`}
                className="flex h-6 w-6 items-center justify-center rounded hover:bg-slate-100 text-slate-600"
              >
                {isExpanded ? (
                  <ChevronDown className="h-4 w-4" />
                ) : (
                  <ChevronRight className="h-4 w-4" />
                )}
              </button>
            ) : (
              <span className="inline-flex h-6 w-6 items-center justify-center text-slate-300">
                <Folder className="h-3.5 w-3.5" />
              </span>
            )}

            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-medium text-sm text-slate-900">{node.name}</span>
                <Badge variant="outline" className="font-mono text-[10px]">
                  {node.code}
                </Badge>
                <Badge variant={node.isActive ? 'success' : 'secondary'} className="text-[10px]">
                  {node.isActive ? 'Active' : 'Inactive'}
                </Badge>
                <span className="text-[11px] text-slate-400">
                  Depth {node.depth} · Sort {node.sortOrder}
                </span>
              </div>
              {node.description && (
                <p className="text-xs text-slate-500 truncate">{node.description}</p>
              )}
            </div>
          </div>

          <PermissionGuard permission="catalog.categories.manage">
            <div className="flex items-center gap-1 shrink-0">
              {node.isActive && node.depth < 4 && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => onAddChild(cat)}
                  aria-label={`Add child under ${node.name}`}
                  title="Add Child Category"
                  className="h-7 px-2 text-xs"
                >
                  <FolderPlus className="h-3.5 w-3.5" />
                </Button>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onEdit(cat)}
                aria-label={`Edit ${node.name}`}
                title="Edit Category"
                className="h-7 px-2 text-xs"
              >
                <Edit3 className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onMove(cat)}
                aria-label={`Move ${node.name}`}
                title="Move Category"
                className="h-7 px-2 text-xs"
              >
                <ArrowRightLeft className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                disabled={pendingCategoryId === node.id}
                onClick={() => onToggleStatus(cat)}
                aria-label={`${node.isActive ? 'Deactivate' : 'Activate'} ${node.name}`}
                title={node.isActive ? 'Deactivate' : 'Activate'}
                className="h-7 px-2 text-xs"
              >
                <Power className="h-3.5 w-3.5" />
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => onDelete(cat)}
                aria-label={`Delete ${node.name}`}
                title="Delete Category"
                className="h-7 px-2 text-xs text-red-600 hover:text-red-700 hover:bg-red-50"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </PermissionGuard>
        </div>

        {hasChildren && isExpanded && (
          <div className="space-y-1">{node.children.map(renderNode)}</div>
        )}
      </div>
    );
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-end gap-2">
        <Button type="button" variant="outline" size="sm" onClick={expandAll} className="h-7 text-xs">
          Expand All
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={collapseAll} className="h-7 text-xs">
          Collapse All
        </Button>
      </div>
      <div className="space-y-1.5">{nodes.map(renderNode)}</div>
    </div>
  );
};
