import type { Permission } from '@/lib/auth/permissions';

export interface NavGroup {
  key: string;
  label: string;
  href: string;
  /** Visible when the user has ANY of these. Empty = every staff user. */
  anyOf: Permission[];
  items: { label: string; href: string; anyOf?: Permission[] }[];
}

/** Admin sidebar — 8 groups, role-filtered (brief §6 "Admin sidebar"). */
export const ADMIN_NAV: NavGroup[] = [
  { key: 'overview', label: 'Overview', href: '/admin', anyOf: [], items: [] },
  {
    key: 'registrations',
    label: 'Registrations',
    href: '/admin/registrations/players',
    anyOf: ['REGISTRATION_VIEW', 'REGISTRATION_REVIEW'],
    items: [
      { label: 'Players', href: '/admin/registrations/players' },
      { label: 'Teams', href: '/admin/registrations/teams' },
      { label: 'Duplicates', href: '/admin/registrations/duplicates' },
    ],
  },
  {
    key: 'directory',
    label: 'Directory',
    href: '/admin/directory/players',
    anyOf: ['REGISTRATION_VIEW', 'DIRECTORY_MANAGE'],
    items: [
      { label: 'Players', href: '/admin/directory/players' },
      { label: 'Teams', href: '/admin/directory/teams' },
      { label: 'Skill categories', href: '/admin/system/settings/categories', anyOf: ['CATEGORY_MANAGE'] },
    ],
  },
  {
    key: 'auction',
    label: 'Auction',
    href: '/admin/auction/setup',
    anyOf: ['AUCTION_CONFIGURE', 'AUCTION_CONTROL'],
    items: [
      { label: 'Setup', href: '/admin/auction/setup', anyOf: ['AUCTION_CONFIGURE'] },
      { label: 'Open console', href: '/console/auction', anyOf: ['AUCTION_CONTROL'] },
      { label: 'Results and budgets', href: '/admin/auction/results' },
    ],
  },
  {
    key: 'competition',
    label: 'Competition',
    href: '/admin/competition/fixtures',
    anyOf: ['FIXTURE_MANAGE'],
    items: [
      { label: 'Format', href: '/admin/competition/format' },
      { label: 'Fixtures', href: '/admin/competition/fixtures' },
      { label: 'Schedule board', href: '/admin/competition/schedule' },
      { label: 'Match categories', href: '/admin/competition/match-categories' },
      { label: 'Scoring rules', href: '/admin/competition/scoring-rules' },
    ],
  },
  {
    key: 'results',
    label: 'Results',
    href: '/admin/results/confirmations',
    anyOf: ['RESULT_CONFIRM', 'STANDINGS_MANAGE'],
    items: [
      { label: 'Confirmations', href: '/admin/results/confirmations', anyOf: ['RESULT_CONFIRM'] },
      { label: 'Standings', href: '/admin/results/standings' },
      { label: 'Statistics', href: '/admin/results/statistics' },
    ],
  },
  {
    key: 'content',
    label: 'Content',
    href: '/admin/content/announcements',
    anyOf: ['CONTENT_MANAGE', 'SETTINGS_MANAGE'],
    items: [
      { label: 'Announcements', href: '/admin/content/announcements', anyOf: ['CONTENT_MANAGE'] },
      { label: 'Sponsors', href: '/admin/content/sponsors', anyOf: ['CONTENT_MANAGE'] },
      { label: 'Committee', href: '/admin/content/committee', anyOf: ['CONTENT_MANAGE'] },
      { label: 'Branding', href: '/admin/system/settings/branding', anyOf: ['SETTINGS_MANAGE'] },
    ],
  },
  {
    key: 'system',
    label: 'System',
    href: '/admin/system/settings',
    anyOf: ['USER_MANAGE', 'AUDIT_VIEW', 'REPORT_VIEW', 'SETTINGS_MANAGE', 'TOURNAMENT_MANAGE'],
    items: [
      { label: 'Users and roles', href: '/admin/system/users', anyOf: ['USER_MANAGE'] },
      { label: 'Audit log', href: '/admin/system/audit', anyOf: ['AUDIT_VIEW'] },
      { label: 'Reports', href: '/admin/system/reports', anyOf: ['REPORT_VIEW'] },
      { label: 'Settings', href: '/admin/system/settings', anyOf: ['TOURNAMENT_VIEW'] },
    ],
  },
];
