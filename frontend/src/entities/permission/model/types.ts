export interface Permission {
  id: string;
  code: string;
  name: string;
  module: string;
  description?: string;
}

export interface UserPermissionsResponse {
  tenantId: string;
  membershipId: string;
  tenantPermissions: string[];
  branchPermissions: Record<string, string[]>;
}
