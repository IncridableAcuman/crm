export interface Permission {
  id: string;
  code: string;
  module: string;
  resource: string;
  action: string;
  description: string;
}

export interface UserPermissionsResponse {
  tenantId: string;
  membershipId: string;
  tenantPermissions: string[];
  branchPermissions: Record<string, string[]>;
}
