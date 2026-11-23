export interface User {
  id: string;
  email: string;
  username: string;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
}

export interface TenantMembership {
  tenantId: string;
  tenantName: string;
  tenantCode: string;
  membershipStatus: 'ACTIVE' | 'INVITED' | 'SUSPENDED';
  current: boolean;
}
