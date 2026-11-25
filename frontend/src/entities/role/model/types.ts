export type RoleScopeType = 'TENANT' | 'BRANCH';

export interface Role {
  id: string;
  code: string;
  name: string;
  description?: string | null;
  isSystem: boolean;
  isActive: boolean;
  permissions: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateRoleRequest {
  code: string;
  name: string;
  description?: string | null;
  permissions: string[];
}

export interface UpdateRoleRequest {
  name: string;
  description?: string | null;
}

export interface RolePermissionResponse {
  roleId: string;
  roleCode: string;
  permissions: string[];
}

export interface RolePermissionUpdateRequest {
  permissions: string[];
}

export interface UserRoleAssignment {
  id: string;
  tenantId: string;
  membershipId: string;
  roleId: string;
  roleCode: string;
  roleName: string;
  scopeType: RoleScopeType;
  branchId?: string | null;
  isActive: boolean;
  createdAt: string;
  revokedAt?: string | null;
}

export interface CreateRoleAssignmentRequest {
  membershipId: string;
  roleId: string;
  scopeType: RoleScopeType;
  branchId?: string | null;
}
