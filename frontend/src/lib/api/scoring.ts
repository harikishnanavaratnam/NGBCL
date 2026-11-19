'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { fixturesKey } from './competition';

// Scoring and results (lk.ngbcl.scoring.web.*)

export type MatchStatus = 'SCHEDULED' | 'LIVE' | 'SUBMITTED' | 'COMPLETED' | 'CANCELLED';
export type Side = 'A' | 'B';
export type Outcome = 'NORMAL' | 'WALKOVER' | 'RETIRED';

export interface Rules {
  pointsToWin: number;
  winBy: number;
  pointCap: number;
  gamesToWin: number;
  custom: boolean;
}

export interface GameScore {
  number: number;
  scoreA: number;
  scoreB: number;
  winnerSide: Side | null;
}

export interface TeamLite {
  slug: string;
  name: string;
  shortCode: string;
  colorSlot: number | null;
  logoUrl: string | null;
}

export interface PlayerName {
  slug: string | null;
  fullName: string;
}

export interface ScorerCard {
  matchId: string;
  fixtureId: string;
  fixtureNumber: number;
  roundName: string;
  groupName: string | null;
  order: number;
  categoryCode: string;
  categoryName: string;
  teamA: TeamLite;
  teamB: TeamLite;
  sideA: PlayerName[];
  sideB: PlayerName[];
  scheduledAt: string | null;
  courtName: string | null;
  status: MatchStatus;
  lineupComplete: boolean;
  gamesA: number;
  gamesB: number;
  returnedReason: string | null;
}

export interface MatchState {
  matchId: string;
  fixtureId: string;
  fixtureNumber: number;
  roundName: string;
  groupName: string | null;
  order: number;
  categoryCode: string;
  categoryName: string;
  teamA: TeamLite;
  teamB: TeamLite;
  sideA: PlayerName[];
  sideB: PlayerName[];
  courtName: string | null;
  scheduledAt: string | null;
  status: MatchStatus;
  rules: Rules;
  games: GameScore[];
  /** Game in play, or null when the match is decided / not started. */
  currentGame: number | null;
  scoreA: number;
  scoreB: number;
  gamesA: number;
  gamesB: number;
  server: Side | null;
  firstServer: Side | null;
  winnerSide: Side | null;
  outcome: Outcome | null;
  outcomeNote: string | null;
  stateVersion: number;
  canUndo: boolean;
  lineupComplete: boolean;
  returnedReason: string | null;
  twoStepApproval: boolean;
  allowWalkover: boolean;
  allowRetirement: boolean;
  submittedAt: string | null;
  submittedBy: string | null;
  scorerName: string | null;
}

export interface PublicLiveMatch {
  matchId: string;
  fixtureNumber: number;
  roundName: string;
  groupName: string | null;
  categoryCode: string;
  categoryName: string;
  teamA: TeamLite;
  teamB: TeamLite;
  sideA: PlayerName[];
  sideB: PlayerName[];
  courtName: string | null;
  scheduledAt: string | null;
  status: MatchStatus;
  provisional: boolean;
  games: GameScore[];
  scoreA: number;
  scoreB: number;
  gamesA: number;
  gamesB: number;
  server: Side | null;
  winnerSide: Side | null;
  outcome: Outcome | null;
  stateVersion: number;
}

export interface PublicLive {
  live: PublicLiveMatch[];
  recent: PublicLiveMatch[];
  upNext: PublicLiveMatch[];
}

export interface ScoringSettings {
  pointsToWin: number;
  winBy: number;
  pointCap: number;
  gamesToWin: number;
  allowWalkover: boolean;
  allowRetirement: boolean;
  twoStepApproval: boolean;
  version: number | null;
}

export function newEventId() {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** Game point / match point / deuce label for the side about to score, from the rules. */
export function situation(s: Pick<MatchState, 'scoreA' | 'scoreB' | 'gamesA' | 'gamesB' | 'rules' | 'currentGame'>): string | null {
  if (s.currentGame === null) return null;
  const { pointsToWin, winBy, pointCap, gamesToWin } = s.rules;
  const wins = (a: number, b: number) => a >= pointCap || (a >= pointsToWin && a - b >= winBy);
  const gpA = wins(s.scoreA + 1, s.scoreB);
  const gpB = wins(s.scoreB + 1, s.scoreA);
  const mpA = gpA && s.gamesA + 1 >= gamesToWin;
  const mpB = gpB && s.gamesB + 1 >= gamesToWin;
  if (mpA || mpB) return 'Match point';
  if (gpA || gpB) return s.scoreA === pointCap - 1 && s.scoreB === pointCap - 1 ? 'Golden point' : 'Game point';
  if (s.scoreA >= pointsToWin - 1 && s.scoreA === s.scoreB) return 'Deuce';
  return null;
}

// ---- Scorer ---------------------------------------------------------------------------------------

const scorerKey = ['scorer', 'matches'] as const;
export const matchKey = (id: string) => ['scorer', 'match', id] as const;

export function useScorerMatches() {
  return useQuery({ queryKey: scorerKey, queryFn: () => api<ScorerCard[]>('/scorer/matches'), refetchInterval: 30_000 });
}

export function useMatchState(id: string | null) {
  return useQuery({ queryKey: matchKey(id ?? ''), queryFn: () => api<MatchState>(`/scorer/matches/${id}`), enabled: Boolean(id) });
}

export function useScorerCommands(id: string) {
  const qc = useQueryClient();
  const done = (s: MatchState) => {
    qc.setQueryData(matchKey(id), (old: MatchState | undefined) => (!old || s.stateVersion >= old.stateVersion ? s : old));
    void qc.invalidateQueries({ queryKey: scorerKey });
  };
  const post = (path: string, body: object) => api<MatchState>(`/scorer/matches/${id}/${path}`, { method: 'POST', body });
  const opts = { onSuccess: done, onError: () => void qc.invalidateQueries({ queryKey: matchKey(id) }) };
  const start = useMutation({ mutationFn: (firstServer: Side) => post('start', { firstServer, clientEventId: newEventId() }), ...opts });
  const point = useMutation({ mutationFn: (v: { side: Side; clientEventId: string }) => post('point', v), ...opts });
  const undo = useMutation({ mutationFn: () => post('undo', { clientEventId: newEventId() }), ...opts });
  const manual = useMutation({
    mutationFn: (v: { games: [number, number][]; firstServer?: Side }) => post('manual', { ...v, clientEventId: newEventId() }),
    ...opts,
  });
  const concede = useMutation({
    mutationFn: (v: { type: 'WALKOVER' | 'RETIRED'; side: Side; note: string }) => post('concede', { ...v, clientEventId: newEventId() }),
    ...opts,
  });
  const submit = useMutation({ mutationFn: () => post('submit', { clientEventId: newEventId() }), ...opts });
  return { start, point, undo, manual, concede, submit };
}

// ---- Officials ----------------------------------------------------------------------------------

const pendingKey = ['results', 'pending'] as const;
const recentKey = ['results', 'recent'] as const;

export function usePendingResults() {
  return useQuery({ queryKey: pendingKey, queryFn: () => api<MatchState[]>('/admin/results/pending'), refetchInterval: 20_000 });
}

export function useRecentResults() {
  return useQuery({ queryKey: recentKey, queryFn: () => api<MatchState[]>('/admin/results/recent') });
}

export function useResultCommands() {
  const qc = useQueryClient();
  const done = () => {
    void qc.invalidateQueries({ queryKey: pendingKey });
    void qc.invalidateQueries({ queryKey: recentKey });
    void qc.invalidateQueries({ queryKey: fixturesKey });
  };
  return {
    confirm: useMutation({
      mutationFn: (id: string) => api<MatchState>(`/admin/results/matches/${id}/confirm`, { method: 'POST' }),
      onSuccess: done,
    }),
    ret: useMutation({
      mutationFn: (v: { id: string; reason: string }) =>
        api<MatchState>(`/admin/results/matches/${v.id}/return`, { method: 'POST', body: { reason: v.reason } }),
      onSuccess: done,
    }),
    correct: useMutation({
      mutationFn: (v: { id: string; games: [number, number][]; outcome: Outcome; winnerSide?: Side; reason: string }) =>
        api<MatchState>(`/admin/results/matches/${v.id}/correct`, {
          method: 'POST',
          body: { games: v.games, outcome: v.outcome, winnerSide: v.winnerSide, reason: v.reason },
        }),
      onSuccess: done,
    }),
  };
}

export function useMatchRules() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { matchId: string; pointsToWin?: number; winBy?: number; pointCap?: number; gamesToWin?: number; reset?: boolean }) =>
      api<MatchState>(`/admin/competition/matches/${v.matchId}/rules`, { method: 'PUT', body: { ...v, reset: Boolean(v.reset) } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: fixturesKey }),
  });
}

const settingsKey = ['scoring', 'settings'] as const;

export function useScoringSettings() {
  return useQuery({ queryKey: settingsKey, queryFn: () => api<ScoringSettings>('/admin/scoring/settings') });
}

export function useSaveScoringSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (s: ScoringSettings) => api<ScoringSettings>('/admin/scoring/settings', { method: 'PUT', body: s }),
    onSuccess: (s) => qc.setQueryData(settingsKey, s),
  });
}

// ---- Public -------------------------------------------------------------------------------------

export const liveKey = ['public', 'live'] as const;

export function usePublicLive() {
  return useQuery({ queryKey: liveKey, queryFn: () => api<PublicLive>('/public/live', { anonymous: true }), refetchInterval: 60_000 });
}
