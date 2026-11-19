import { cacheLife, cacheTag } from 'next/cache';
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
  cacheTag('tournament');
  try {
    const res = await fetch(`${API_URL}/api/public/tournament`, { headers: { Accept: 'application/json' } });
    if (!res.ok) return null;
    return (await res.json()) as PublicTournament;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------------------------
// Public website data (M3). Every fetcher is cached for a few minutes and tagged, so the API can
// refresh pages immediately after a change (POST /api/revalidate). On API errors they return
// null / [] and pages show their empty states instead of failing the build.
// ---------------------------------------------------------------------------------------------

export interface PublicTeam {
  slug: string;
  name: string;
  shortCode: string;
  colorSlot: number | null;
  description: string | null;
  logoUrl: string | null;
}
export interface SquadMember {
  slug: string;
  fullName: string;
  categoryName: string | null;
  photoUrl: string | null;
  /** Null when auction prices are hidden in settings. */
  price: number | null;
  lotNumber: number | null;
}
export interface PublicTeamDetail extends PublicTeam {
  squad: SquadMember[];
}
export interface PublicPlayer {
  slug: string;
  fullName: string;
  categoryName: string | null;
  photoUrl: string | null;
  /** Set once the player is bought at the auction. */
  teamSlug: string | null;
  teamName: string | null;
  teamColorSlot: number | null;
  /** Null when not sold or when auction prices are hidden in settings. */
  price: number | null;
}
export interface PublicPlayerDetail extends PublicPlayer {
  experience: string | null;
  lotNumber: number | null;
}
export interface PublicNewsItem {
  slug: string;
  title: string;
  excerpt: string;
  coverUrl: string | null;
  pinned: boolean;
  publishedAt: string;
}
export interface PublicNewsArticle {
  slug: string;
  title: string;
  body: string;
  coverUrl: string | null;
  publishedAt: string;
}
export type SponsorTier = 'TITLE' | 'PLATINUM' | 'GOLD' | 'SILVER' | 'BRONZE' | 'MEDIA';
export interface PublicSponsor {
  name: string;
  tier: SponsorTier;
  logoUrl: string | null;
  websiteUrl: string | null;
  awardName: string | null;
  showOnWall: boolean;
  showOnTicker: boolean;
}
export interface PublicCommitteeGroup {
  name: string;
  style: 'LEAD' | 'COMPACT';
  members: { fullName: string; role: string; bio: string | null; photoUrl: string | null }[];
}
interface NewsPage {
  content: PublicNewsItem[];
  page: number;
  totalPages: number;
  totalElements: number;
}

async function getJson<T>(path: string): Promise<T | null> {
  try {
    const res = await fetch(`${API_URL}/api/public${path}`, { headers: { Accept: 'application/json' } });
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

export async function getPublicTeams(): Promise<PublicTeam[]> {
  'use cache';
  cacheLife('minutes');
  cacheTag('teams');
  return (await getJson<PublicTeam[]>('/teams')) ?? [];
}

export async function getPublicTeam(slug: string): Promise<PublicTeamDetail | null> {
  'use cache';
  cacheLife('minutes');
  cacheTag('teams');
  return getJson<PublicTeamDetail>(`/teams/${encodeURIComponent(slug)}`);
}

export async function getPublicPlayers(): Promise<PublicPlayer[]> {
  'use cache';
  cacheLife('minutes');
  cacheTag('players');
  return (await getJson<PublicPlayer[]>('/players')) ?? [];
}

export async function getPublicPlayer(slug: string): Promise<PublicPlayerDetail | null> {
  'use cache';
  cacheLife('minutes');
  cacheTag('players');
  return getJson<PublicPlayerDetail>(`/players/${encodeURIComponent(slug)}`);
}

export async function getNews(page = 0, size = 12): Promise<NewsPage> {
  'use cache';
  cacheLife('minutes');
  cacheTag('news');
  return (await getJson<NewsPage>(`/news?page=${page}&size=${size}`)) ?? { content: [], page: 0, totalPages: 0, totalElements: 0 };
}

export async function getArticle(slug: string): Promise<PublicNewsArticle | null> {
  'use cache';
  cacheLife('minutes');
  cacheTag('news');
  return getJson<PublicNewsArticle>(`/news/${encodeURIComponent(slug)}`);
}

export async function getSponsors(): Promise<PublicSponsor[]> {
  'use cache';
  cacheLife('minutes');
  cacheTag('sponsors');
  return (await getJson<PublicSponsor[]>('/sponsors')) ?? [];
}

export async function getCommittee(): Promise<PublicCommitteeGroup[]> {
  'use cache';
  cacheLife('minutes');
  cacheTag('committee');
  return (await getJson<PublicCommitteeGroup[]>('/committee')) ?? [];
}

/** Slugs to prerender at build time. Cache Components needs at least one, so fall back to a placeholder. */
export async function staticSlugs(kind: 'teams' | 'players' | 'news'): Promise<{ slug: string }[]> {
  const list =
    kind === 'teams'
      ? await getPublicTeams()
      : kind === 'players'
        ? (await getPublicPlayers()).slice(0, 50)
        : (await getNews(0, 20)).content;
  const slugs = list.map((x) => ({ slug: x.slug }));
  return slugs.length ? slugs : [{ slug: '_' }];
}

// ---- Fixtures (M5) — published copies only ----------------------------------------------------

export interface PublicFixtureTeam {
  slug: string;
  name: string;
  shortCode: string;
  colorSlot: number | null;
  logoUrl: string | null;
}
export interface PublicFixtureMatch {
  order: number;
  categoryCode: string;
  categoryName: string;
  sideA: { slug: string | null; fullName: string }[];
  sideB: { slug: string | null; fullName: string }[];
  scheduledAt: string | null;
  status: string;
}
export interface PublicFixture {
  number: number;
  stageName: string;
  stageType: 'GROUP' | 'LEAGUE' | 'KNOCKOUT';
  groupName: string | null;
  roundNumber: number;
  roundName: string;
  bracketPosition: number | null;
  teamA: PublicFixtureTeam | null;
  teamB: PublicFixtureTeam | null;
  sourceA: string | null;
  sourceB: string | null;
  scheduledAt: string | null;
  courtName: string | null;
  status: string;
  winnerToNumber: number | null;
  matches: PublicFixtureMatch[];
}
export interface PublicFixtures {
  format: 'GROUPS_KNOCKOUT' | 'ROUND_ROBIN' | 'ROUND_ROBIN_FINAL' | 'KNOCKOUT' | null;
  groups: { name: string; teams: PublicFixtureTeam[] }[];
  fixtures: PublicFixture[];
}

export async function getPublicFixtures(): Promise<PublicFixtures> {
  'use cache';
  cacheLife('minutes');
  cacheTag('fixtures');
  return (await getJson<PublicFixtures>('/fixtures')) ?? { format: null, groups: [], fixtures: [] };
}
