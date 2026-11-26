import { User, TenantMembership } from '../../user/model/types';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
  activeTenant?: TenantMembership | null;
  availableTenants: TenantMembership[];
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  error?: unknown;
  timestamp: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
