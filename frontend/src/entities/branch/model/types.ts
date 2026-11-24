export interface Branch {
  id: string;
  tenantId: string;
  code: string;
  name: string;
  phone?: string | null;
  address?: string | null;
  timezone: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBranchRequest {
  code: string;
  name: string;
  phone?: string | null;
  address?: string | null;
  timezone: string;
}
