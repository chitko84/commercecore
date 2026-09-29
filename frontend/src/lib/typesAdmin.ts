import type { AdminUserResponse } from './types';

export interface AdminUserPage {
  content: AdminUserResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}