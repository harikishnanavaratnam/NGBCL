'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { fixturesKey } from './competition';
import type { StandingTeam, Standings, StandingsRules, Stats, Tiebreaker } from './public';

export type { StandingTeam, Standings, StandingsRules, Stats, Tiebreaker };

export interface SeedingSlot {
  fixtureId: string;
  fixtureNumber: number;
  roundName: string;
  side: 'A' | 'B';
  source: string;
  currentTeam: StandingTeam | null;
  proposedTeam: StandingTeam | null;
  ready: boolean;
  locked: boolean;
  changes: boolean;
  note: string | null;
}

export interface Seeding {
  allGroupsComplete: boolean;
  pendingChanges: number;
  slots: SeedingSlot[];
}

const standingsKey = ['standings', 'admin'] as const;
const seedingKey = ['standings', 'seeding'] as const;
const rulesKey = ['standings', 'settings'] as const;

export function useAdminStandings() {
  return useQuery({ queryKey: standingsKey, queryFn: () => api<Standings>('/admin/standings') });
}

export function useSeeding(enabled = true) {
  return useQuery({ queryKey: seedingKey, queryFn: () => api<Seeding>('/admin/standings/seeding'), enabled });
}

export function useApplySeeding() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<{ tiesUpdated: number; view: Seeding }>('/admin/standings/seeding/apply', { method: 'POST' }),
    onSuccess: (r) => {
      qc.setQueryData(seedingKey, r.view);
      void qc.invalidateQueries({ queryKey: fixturesKey });
    },
  });
}

export function useStandingsRules() {
  return useQuery({ queryKey: rulesKey, queryFn: () => api<StandingsRules>('/admin/standings/settings') });
}

export function useSaveStandingsRules() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (r: Omit<StandingsRules, 'version'> & { version: number | null }) =>
      api<StandingsRules>('/admin/standings/settings', { method: 'PUT', body: r }),
    onSuccess: (r) => {
      qc.setQueryData(rulesKey, r);
      void qc.invalidateQueries({ queryKey: standingsKey });
      void qc.invalidateQueries({ queryKey: seedingKey });
    },
  });
}

export function usePublicStatsClient() {
  return useQuery({ queryKey: ['public', 'stats'], queryFn: () => api<Stats>('/public/stats', { anonymous: true }) });
}

// ---- Reports ------------------------------------------------------------------------------------

export type ReportKind = 'results' | 'standings' | 'players' | 'auction' | 'audit';

export interface ReportSheet {
  name: string;
  columns: string[];
  rows: (string | number | null)[][];
}

export interface Report {
  title: string;
  fileName: string;
  generatedAt: string;
  sheets: ReportSheet[];
}

export function fetchReport(kind: ReportKind, query?: Record<string, string | undefined>) {
  return api<Report>(`/admin/reports/${kind}`, { query });
}
