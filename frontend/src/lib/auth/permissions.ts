/**
 * Mirrors lk.ngbcl.security.Permission on the backend.
 * UI gating ONLY — the API enforces every one of these server-side (SRS §2.1, §12).
 */
export const PERMISSIONS = [
  'TOURNAMENT_VIEW',
  'TOURNAMENT_MANAGE',
  'SETTINGS_MANAGE',
  'REGISTRATION_VIEW',
  'REGISTRATION_REVIEW',
  'DIRECTORY_MANAGE',
  'CATEGORY_MANAGE',
  'AUCTION_CONFIGURE',
  'AUCTION_CONTROL',
  'AUCTION_CORRECT',
  'FIXTURE_MANAGE',
  'MATCH_SCORE',
  'MATCH_SCORE_ANY',
  'RESULT_CONFIRM',
  'RESULT_CORRECT',
  'STANDINGS_MANAGE',
  'CONTENT_MANAGE',
  'USER_MANAGE',
  'AUDIT_VIEW',
  'REPORT_VIEW',
  'FILE_UPLOAD',
] as const;
export type Permission = (typeof PERMISSIONS)[number];

/** Staff roles. Only staff sign in; players, teams and the public never need an account. */
export const ROLES = [
  'SUPER_ADMIN',
  'TOURNAMENT_ADMIN',
  'REGISTRATION_MANAGER',
  'AUCTIONEER',
  'MATCH_SCORER',
] as const;
export type Role = (typeof ROLES)[number];

export const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Super Admin',
  TOURNAMENT_ADMIN: 'Tournament Admin',
  REGISTRATION_MANAGER: 'Registration Manager',
  AUCTIONEER: 'Auctioneer',
  MATCH_SCORER: 'Match Scorer',
};

export function hasAny(granted: readonly Permission[] | undefined, required: readonly Permission[]) {
  if (!required.length) return true;
  if (!granted) return false;
  return required.some((p) => granted.includes(p));
}
