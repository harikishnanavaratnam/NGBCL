'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';

// Auction (lk.ngbcl.auction.web.AuctionController / PublicAuctionController)

export type AuctionStatus = 'NOT_STARTED' | 'LIVE' | 'PAUSED' | 'COMPLETED';
export type LotState = 'QUEUED' | 'OPEN' | 'SOLD' | 'UNSOLD' | 'WITHDRAWN';
export type CallState = 'NONE' | 'GOING_ONCE' | 'GOING_TWICE';
export type BidStatus = 'ACCEPTED' | 'VOIDED';

export interface AuctionSettings {
  initialBudget: number;
  maxSquadSize: number;
  minSquadSize: number | null;
  defaultBasePrice: number;
  minIncrement: number;
  allowReauction: boolean;
  allowManualAdjustment: boolean;
  startsAt: string | null;
  /** True once the auction has started: budget is fixed, other changes need a reason. */
  locked: boolean;
  version: number | null;
}

export interface AuctionSettingsInput extends Omit<AuctionSettings, 'locked'> {
  reason?: string | null;
}

export interface LotPlayer {
  entryId: string;
  slug: string;
  fullName: string;
  categoryName: string | null;
  photoUrl: string | null;
}

export interface Bid {
  id: string;
  teamId: string;
  teamName: string | null;
  teamShortCode: string | null;
  teamColorSlot: number | null;
  amount: number;
  bidNumber: number;
  status: BidStatus;
  createdAt: string;
}

export interface Lot {
  id: string;
  lotNumber: number | null;
  round: number;
  state: LotState;
  callState: CallState;
  basePrice: number;
  basePriceOverridden: boolean;
  currentAmount: number | null;
  leadingTeamId: string | null;
  nextMinimum: number;
  finalPrice: number | null;
  winningTeamId: string | null;
  player: LotPlayer | null;
  /** Newest first, including voided (undone) bids. */
  bids: Bid[];
  openedAt: string | null;
}

export interface QueueItem {
  lotId: string;
  entryId: string;
  fullName: string;
  categoryName: string | null;
  photoUrl: string | null;
  basePrice: number;
  basePriceOverridden: boolean;
  state: LotState;
  round: number;
  queueOrder: number;
  lotNumber: number | null;
  finalPrice: number | null;
  winningTeamId: string | null;
  winningTeamName: string | null;
}

export type BlockedReason = 'SQUAD_FULL' | 'LEADING' | 'BUDGET' | 'RESERVE' | 'NO_LOT' | AuctionStatus;

export interface TeamRail {
  teamId: string;
  slug: string;
  name: string;
  shortCode: string;
  colorSlot: number | null;
  logoUrl: string | null;
  initialBudget: number;
  adjustments: number;
  spent: number;
  remaining: number;
  squadCount: number;
  maxSquadSize: number;
  maxBid: number;
  canBid: boolean;
  blockedReason: BlockedReason | null;
}

export interface ConsoleSnapshot {
  auctionId: string;
  status: AuctionStatus;
  round: number;
  stateVersion: number;
  lotsOpened: number;
  startedAt: string | null;
  lastActionAt: string | null;
  lastEvent: string | null;
  settings: AuctionSettings;
  currentLot: Lot | null;
  queue: QueueItem[];
  teams: TeamRail[];
  counts: { total: number; queued: number; sold: number; unsold: number; withdrawn: number };
  canStartNextRound: boolean;
  startBlockers: string[];
}

export interface BidResult {
  bidId: string;
  lotId: string;
  teamId: string;
  amount: number;
  bidNumber: number;
  stateVersion: number;
  replayed: boolean;
}

export interface RosterEntry {
  lotId: string | null;
  entryId: string;
  slug: string | null;
  fullName: string;
  categoryName: string | null;
  photoUrl: string | null;
  source: 'AUCTION' | 'ADMIN';
  purchasePrice: number | null;
  lotNumber: number | null;
}

export interface TeamRoster {
  team: TeamRail;
  players: RosterEntry[];
}

// ---- Public -------------------------------------------------------------------------------

export interface PublicTeamTicker {
  slug: string;
  name: string;
  shortCode: string;
  colorSlot: number | null;
  logoUrl: string | null;
  squadCount: number;
  maxSquadSize: number;
  /** Null when team budgets are hidden in settings. */
  remaining: number | null;
  spent: number | null;
}

export interface PublicBid {
  teamName: string | null;
  teamShortCode: string | null;
  teamColorSlot: number | null;
  amount: number;
}

export interface PublicLot {
  lotNumber: number | null;
  round: number;
  state: LotState;
  callState: CallState;
  slug: string | null;
  fullName: string | null;
  categoryName: string | null;
  photoUrl: string | null;
  basePrice: number;
  currentAmount: number | null;
  leadingTeam: PublicTeamTicker | null;
  lastBids: PublicBid[];
  finalPrice: number | null;
  winningTeam: PublicTeamTicker | null;
}

export interface PublicBuy {
  lotNumber: number | null;
  slug: string | null;
  fullName: string | null;
  categoryName: string | null;
  photoUrl: string | null;
  teamName: string | null;
  teamSlug: string | null;
  teamColorSlot: number | null;
  /** Null when prices are hidden in settings. */
  price: number | null;
}

export interface PublicAuctionState {
  status: AuctionStatus;
  round: number;
  stateVersion: number;
  lotsOpened: number;
  totalLots: number;
  startsAt: string | null;
  currentLot: PublicLot | null;
  lastClosed: PublicLot | null;
  teams: PublicTeamTicker[];
  topBuys: PublicBuy[];
  budgetsPublic: boolean;
}

export interface PublicResults {
  status: AuctionStatus;
  sold: PublicBuy[];
  unsold: PublicBuy[];
  teams: PublicTeamTicker[];
  budgetsPublic: boolean;
  pricesPublic: boolean;
}

/** Realtime topic every auction change is broadcast on (payload = PublicAuctionState). */
export const AUCTION_TOPIC = '/topic/public/auction';

// ---- Hooks ----------------------------------------------------------------------------------

export const consoleKey = ['auction', 'console'] as const;
const settingsKey = ['auction', 'settings'] as const;
const rostersKey = ['auction', 'rosters'] as const;
export const publicStateKey = ['auction', 'public'] as const;

export function useAuctionSettings() {
  return useQuery({ queryKey: settingsKey, queryFn: () => api<AuctionSettings>('/admin/auction/settings') });
}

export function useSaveAuctionSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: AuctionSettingsInput) => api<AuctionSettings>('/admin/auction/settings', { method: 'PUT', body }),
    onSuccess: (s) => {
      qc.setQueryData(settingsKey, s);
      void qc.invalidateQueries({ queryKey: consoleKey });
    },
  });
}

/** Staff snapshot. The GET also brings the queue in line with the approved player list. */
export function useAuctionConsole(enabled = true) {
  return useQuery({
    queryKey: consoleKey,
    queryFn: () => api<ConsoleSnapshot>('/admin/auction'),
    enabled,
    refetchOnWindowFocus: true,
  });
}

/** Every console command answers with a fresh snapshot, so the screen never shows stale state. */
function useCommand<V>(path: (v: V) => string, body?: (v: V) => unknown, method: 'POST' | 'PUT' = 'POST') {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: V) => api<ConsoleSnapshot>(path(v), { method, body: body ? body(v) : undefined }),
    onSuccess: (snap) => qc.setQueryData(consoleKey, snap),
    onError: () => void qc.invalidateQueries({ queryKey: consoleKey }),
  });
}

export function useAuctionCommands() {
  return {
    start: useCommand<void>(() => '/admin/auction/start'),
    pause: useCommand<void>(() => '/admin/auction/pause'),
    resume: useCommand<void>(() => '/admin/auction/resume'),
    end: useCommand<{ reason?: string }>(() => '/admin/auction/end', (v) => v),
    open: useCommand<{ lotId?: string }>(() => '/admin/auction/lots/open', (v) => v),
    call: useCommand<{ lotId: string; state: CallState }>(() => '/admin/auction/lots/call', (v) => v),
    undo: useCommand<{ lotId: string }>(() => '/admin/auction/bids/undo', (v) => v),
    sell: useCommand<{ lotId: string; bidId: string }>(() => '/admin/auction/lots/sell', (v) => v),
    unsold: useCommand<{ lotId: string }>(() => '/admin/auction/lots/unsold', (v) => v),
    withdraw: useCommand<{ lotId: string; reason: string }>((v) => `/admin/auction/lots/${v.lotId}/withdraw`, (v) => ({ reason: v.reason })),
    restore: useCommand<{ lotId: string }>((v) => `/admin/auction/lots/${v.lotId}/restore`),
    nextRound: useCommand<{ lotIds?: string[] }>(() => '/admin/auction/rounds/next', (v) => v),
    reorder: useCommand<{ lotIds: string[] }>(() => '/admin/auction/queue/order', (v) => v, 'PUT'),
    basePrice: useCommand<{ entryId: string; basePrice: number | null }>(
      (v) => `/admin/auction/players/${v.entryId}/base-price`,
      (v) => ({ basePrice: v.basePrice }),
      'PUT',
    ),
  };
}

export interface BidInput {
  lotId: string;
  teamId: string;
  amount: number;
  /** Same key on retry → the server records the bid once (AUC-030). */
  idempotencyKey: string;
}

export function usePlaceBid() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ idempotencyKey, ...body }: BidInput) =>
      api<{ bid: BidResult; snapshot: ConsoleSnapshot }>('/admin/auction/bids', {
        method: 'POST',
        body,
        headers: { 'Idempotency-Key': idempotencyKey },
      }),
    onSuccess: (r) => qc.setQueryData(consoleKey, r.snapshot),
    onError: () => void qc.invalidateQueries({ queryKey: consoleKey }),
  });
}

export function useRosters() {
  return useQuery({ queryKey: rostersKey, queryFn: () => api<TeamRoster[]>('/admin/auction/rosters') });
}

export function useCorrections() {
  const qc = useQueryClient();
  const done = (r: TeamRoster[]) => {
    qc.setQueryData(rostersKey, r);
    void qc.invalidateQueries({ queryKey: consoleKey });
  };
  return {
    release: useMutation({
      mutationFn: (v: { lotId: string; reason: string }) =>
        api<TeamRoster[]>(`/admin/auction/lots/${v.lotId}/release`, { method: 'POST', body: { reason: v.reason } }),
      onSuccess: done,
    }),
    move: useMutation({
      mutationFn: (v: { lotId: string; teamId: string; price: number | null; reason: string }) =>
        api<TeamRoster[]>(`/admin/auction/lots/${v.lotId}/move`, { method: 'POST', body: { teamId: v.teamId, price: v.price, reason: v.reason } }),
      onSuccess: done,
    }),
    adjust: useMutation({
      mutationFn: (v: { teamId: string; amount: number; reason: string }) =>
        api<TeamRoster[]>(`/admin/auction/teams/${v.teamId}/adjust`, { method: 'POST', body: { amount: v.amount, reason: v.reason } }),
      onSuccess: done,
    }),
  };
}

export function usePublicAuctionState() {
  return useQuery({
    queryKey: publicStateKey,
    queryFn: () => api<PublicAuctionState>('/public/auction/state', { anonymous: true }),
    refetchOnWindowFocus: true,
  });
}

export function usePublicAuctionResults() {
  return useQuery({
    queryKey: ['auction', 'results'],
    queryFn: () => api<PublicResults>('/public/auction/results', { anonymous: true }),
  });
}

/** Human text for the reasons a team can't bid (rail chips). */
export const BLOCKED_LABEL: Record<BlockedReason, string> = {
  SQUAD_FULL: 'Squad full',
  LEADING: 'Leading',
  BUDGET: 'Over budget',
  RESERVE: 'Reserve',
  NO_LOT: '',
  NOT_STARTED: 'Not started',
  LIVE: '',
  PAUSED: 'Paused',
  COMPLETED: 'Ended',
};

export function newIdempotencyKey() {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
