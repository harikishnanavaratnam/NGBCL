/** JS mirror of tokens needed in logic (team colours, breakpoints). CSS tokens live in ngbcl-ds.css. */

/** Team palette — max 10, used only as a 4 px band, crest ring and chart series (brief §3). */
export const TEAM_COLORS = [
  { slot: 1, name: 'Ember', hex: '#F05A28' },
  { slot: 2, name: 'Lagoon', hex: '#1BA6B5' },
  { slot: 3, name: 'Royal', hex: '#2F6FED' },
  { slot: 4, name: 'Plum', hex: '#A23B72' },
  { slot: 5, name: 'Violet', hex: '#8B5CF6' },
  { slot: 6, name: 'Rose', hex: '#E0457B' },
  { slot: 7, name: 'Forest', hex: '#2E9E5B' },
  { slot: 8, name: 'Sky', hex: '#38BDF8' },
  { slot: 9, name: 'Copper', hex: '#B8673A' },
  { slot: 10, name: 'Slate', hex: '#64748B' },
] as const;

export function teamColor(slot: number | null | undefined) {
  return TEAM_COLORS.find((c) => c.slot === slot)?.hex ?? 'var(--line-3)';
}

export const SPONSOR_TIERS = {
  TITLE: '#C9A45C',
  PLATINUM: '#D9DEE7',
  GOLD: '#E2B84A',
  SILVER: '#A9B4CC',
  BRONZE: '#B8733A',
  MEDIA: '#A5ADB8',
} as const;

export const BREAKPOINTS = { sm: 360, md: 768, lg: 1024, xl: 1280, xxl: 1600, display: 1920 } as const;
