'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { FileAssetView } from './files';

export type TournamentStatus = 'DRAFT' | 'UPCOMING' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
export type TournamentPhase = 'PRE_LAUNCH' | 'REGISTRATION' | 'REGISTRATION_CLOSED' | 'AUCTION' | 'RUNNING' | 'COMPLETED';
export type WindowState = 'NOT_CONFIGURED' | 'NOT_OPEN' | 'OPEN' | 'CLOSED';

export interface FieldRule {
  collect: boolean;
  required: boolean;
}
export interface PlayerFieldRules {
  dateOfBirth: FieldRule;
  gender: FieldRule;
  address: FieldRule;
  homeArea: FieldRule;
  experience: FieldRule;
  emergencyContact: FieldRule;
}

export interface Settings {
  general: {
    id: string;
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
    courts: number | null;
    status: TournamentStatus;
    timezone: string;
    currency: string;
    phase: TournamentPhase;
    tournamentVersion: number;
  };
  publicInfo: {
    facebookUrl: string | null;
    contactEmail: string | null;
    contactPhone: string | null;
    prizeFirst: number | null;
    prizeSecond: number | null;
    prizeThird: number | null;
    teamEntryFee: number | null;
    showPlayerPrices: boolean;
    showTeamBudgets: boolean;
    phaseOverride: TournamentPhase | null;
  };
  registration: {
    playerOpensAt: string | null;
    playerClosesAt: string | null;
    playerState: WindowState;
    teamOpensAt: string | null;
    teamClosesAt: string | null;
    teamState: WindowState;
    categoryRequired: boolean;
    playerFields: PlayerFieldRules;
    dupMatchEmail: boolean;
    dupMatchPhone: boolean;
    dupMatchNameDob: boolean;
    maxPhotoBytes: number;
    minPhotoWidth: number;
    minPhotoHeight: number;
    notifyOnRegistration: boolean;
  };
  branding: {
    logo: FileAssetView | null;
    favicon: FileAssetView | null;
    organiserLogo: FileAssetView | null;
    poster: FileAssetView | null;
  };
  settingsVersion: number;
  updatedAt: string | null;
}

export type GeneralUpdate = Omit<Settings['general'], 'id' | 'slug' | 'edition' | 'status' | 'timezone' | 'currency' | 'phase'> & {
  publicInfo: Settings['publicInfo'];
  settingsVersion: number;
};
export type RegistrationUpdate = Omit<Settings['registration'], 'playerState' | 'teamState'> & { settingsVersion: number };
export interface BrandingUpdate {
  logoAssetId: string | null;
  faviconAssetId: string | null;
  organiserLogoAssetId: string | null;
  posterAssetId: string | null;
  settingsVersion: number;
}

export const settingsKey = ['admin', 'settings'] as const;

export function useSettings() {
  return useQuery({ queryKey: settingsKey, queryFn: () => api<Settings>('/admin/settings') });
}

function useSettingsMutation<V>(path: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: V) => api<Settings>(path, { method: 'PUT', body }),
    onSuccess: (data) => qc.setQueryData(settingsKey, data),
  });
}

export const useSaveGeneral = () => useSettingsMutation<GeneralUpdate>('/admin/settings/general');
export const useSaveRegistration = () => useSettingsMutation<RegistrationUpdate>('/admin/settings/registration');
export const useSaveBranding = () => useSettingsMutation<BrandingUpdate>('/admin/settings/branding');
export const useChangeStatus = () => useSettingsMutation<{ status: TournamentStatus; reason?: string }>('/admin/tournament/status');

/** Registration payload from the current settings (used when one panel changes a single field). */
export function registrationPayload(s: Settings, patch: Partial<RegistrationUpdate> = {}): RegistrationUpdate {
  const { playerState: _p, teamState: _t, ...rest } = s.registration;
  void _p;
  void _t;
  return { ...rest, settingsVersion: s.settingsVersion, ...patch };
}
