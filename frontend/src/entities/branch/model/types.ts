export interface Branch {
  id: string;
  tenantId: string;
  code: string;
  name: string;
  phone?: string | null;
  address?: string | null;
  isActive: boolean;
  isMain: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBranchRequest {
  code: string;
  name: string;
  phone?: string | null;
  address?: string | null;
  isMain: boolean;
}
