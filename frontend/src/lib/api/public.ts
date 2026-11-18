import { cacheLife } from 'next/cache';
import { API_URL } from './client';
import type { TournamentPhase, TournamentStatus, WindowState } from './settings';

export interface PublicTournament {
  slug: string;
  name: string;
  shortName: string;
  edition: string;
  tagline: string | null;
  description: string | null;
  organiser: string | null;
  presenterLine: string | null;
  startsOn: string | null;
  endsOn: string | null;
  venue: string | null;
  city: string | null;
  status: TournamentStatus;
  phase: TournamentPhase;
  playerRegistration: { state: WindowState; opensAt: string | null; closesAt: string | null };
  teamRegistration: { state: WindowState; opensAt: string | null; closesAt: string | null };
  prizes: { first: number | null; second: number | null; third: number | null };
  teamEntryFee: number | null;
  facebookUrl: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  branding: { logoUrl: string | null; faviconUrl: string | null; organiserLogoUrl: string | null; posterUrl: string | null };
}

/**
 * Current edition for public pages. Cached for a few minutes (Cache Components); if the API
 * is unreachable (e.g. during a build) we return null and the pages show "TBC" placeholders.
 */
export async function getPublicTournament(): Promise<PublicTournament | null> {
  'use cache';
  cacheLife('minutes');
  try {
    const res = await fetch(`${API_URL}/api/public/tournament`, { headers: { Accept: 'application/json' } });
    if (!res.ok) return null;
    return (await res.json()) as PublicTournament;
  } catch {
    return null;
  }
}
