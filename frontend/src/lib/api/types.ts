/**
 * M0 API contract (hand-written). From M1 onward these are generated from the backend's
 * OpenAPI document with `pnpm gen:api` into ./schema.d.ts — keep names aligned.
 */
import type { Permission, Role } from '@/lib/auth/permissions';

export type HomeSurface = 'ADMIN' | 'AUCTION_CONSOLE' | 'SCORER';

export interface Me {
  id: string;
  email: string;
  displayName: string;
  roles: Role[];
  permissions: Permission[];
  homeSurface: HomeSurface;
}

export interface TokenResponse {
  accessToken: string;
  /** Seconds until the access token expires. */
  expiresIn: number;
  user: Me;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
