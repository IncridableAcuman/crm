export interface Category {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  description: string | null;
  depth: number;
  sortOrder: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CategorySummary {
  id: string;
  code: string;
  name: string;
}

export interface CategoryTreeNode {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  description: string | null;
  depth: number;
  sortOrder: number;
  isActive: boolean;
  children: CategoryTreeNode[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateCategoryRequest {
  parentId?: string | null;
  code: string;
  name: string;
  description?: string | null;
  sortOrder?: number | null;
}

export interface UpdateCategoryRequest {
  name: string;
  description?: string | null;
  sortOrder?: number | null;
}

export interface MoveCategoryRequest {
  newParentId: string | null;
}
