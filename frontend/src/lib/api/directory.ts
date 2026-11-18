'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { Page } from './types';

// Player directory (lk.ngbcl.player.web.PlayerController)

export type TournamentPlayerStatus = 'APPROVED' | 'WITHDRAWN' | 'INACTIVE';

export interface PlayerRow {
  id: string;
  playerId: string;
  slug: string;
  fullName: string;
  email: string;
  phone: string;
  homeArea: string | null;
  gender: string | null;
  dateOfBirth: string | null;
  categoryId: string | null;
  categoryName: string | null;
  status: TournamentPlayerStatus;
  statusReason: string | null;
  photoUrl: string | null;
  registrationId: string;
  version: number;
}

export interface DirectoryFilter {
  q?: string;
  status?: TournamentPlayerStatus;
  categoryId?: string;
  page: number;
  size: number;
}

const playersKey = ['admin', 'players'] as const;

export function usePlayerDirectory(f: DirectoryFilter) {
  return useQuery({
    queryKey: [...playersKey, f],
    queryFn: () => api<Page<PlayerRow>>('/admin/players', { query: { ...f } }),
    placeholderData: keepPreviousData,
  });
}

export function usePlayerStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...b }: { id: string; status: TournamentPlayerStatus; reason?: string; version: number }) =>
      api<PlayerRow>(`/admin/players/${id}/status`, { method: 'POST', body: b }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: playersKey }),
  });
}

export function usePlayerCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...b }: { id: string; categoryId: string | null; version: number }) =>
      api<PlayerRow>(`/admin/players/${id}/category`, { method: 'POST', body: b }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: playersKey }),
  });
}

// Teams (lk.ngbcl.team.web.TeamController)

export type TeamSource = 'PUBLIC_REGISTRATION' | 'ADMIN';

export interface Team {
  id: string;
  name: string;
  slug: string;
  shortCode: string;
  colorSlot: number | null;
  description: string | null;
  logoUrl: string | null;
  logoAssetId: string | null;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  creationSource: TeamSource;
  status: 'ACTIVE' | 'INACTIVE';
  feeReceived: boolean;
  feeReceivedAt: string | null;
  teamRegistrationId: string | null;
  version: number;
}

export interface TeamInput {
  name: string;
  shortCode?: string;
  colorSlot?: number | null;
  description?: string | null;
  logoAssetId?: string | null;
  contactName?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
}

const teamsKey = ['admin', 'teams'] as const;

export function useTeams() {
  return useQuery({ queryKey: teamsKey, queryFn: () => api<Team[]>('/admin/teams') });
}

export function useCreateTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (b: TeamInput) => api<Team>('/admin/teams', { method: 'POST', body: b }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: teamsKey }),
  });
}

export function useUpdateTeam() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...b }: TeamInput & { id: string; version: number }) =>
      api<Team>(`/admin/teams/${id}`, { method: 'PATCH', body: b }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: teamsKey }),
  });
}

export function useTeamFee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...b }: { id: string; received: boolean; version: number }) =>
      api<Team>(`/admin/teams/${id}/fee`, { method: 'POST', body: b }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: teamsKey }),
  });
}

export function useTeamStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...b }: { id: string; status: 'ACTIVE' | 'INACTIVE'; reason?: string; version: number }) =>
      api<Team>(`/admin/teams/${id}/status`, { method: 'POST', body: b }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: teamsKey }),
  });
}
