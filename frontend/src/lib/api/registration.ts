'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import { ApiError } from './errors';
import type { FileAssetView } from './files';
import type { PlayerFieldRules, WindowState } from './settings';
import type { Page } from './types';

// ---------------------------------------------------------------------------------------------
// Public registration (anonymous). Mirrors lk.ngbcl.registration.web.PublicRegistrationController.
// ---------------------------------------------------------------------------------------------

export type RegistrationKind = 'PLAYER' | 'TEAM';
export type RegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export interface PublicWindow {
  state: WindowState;
  opensAt: string | null;
  closesAt: string | null;
}
export interface PhotoRules {
  maxBytes: number;
  minWidth: number;
  minHeight: number;
  ratioW: number;
  ratioH: number;
}
export interface PublicCategory {
  id: string;
  name: string;
  description: string | null;
}
export interface PlayerForm {
  window: PublicWindow;
  fields: PlayerFieldRules;
  categoryRequired: boolean;
  categories: PublicCategory[];
  photo: PhotoRules;
  consentVersion: string;
  turnstileSiteKey: string | null;
  serverTime: string;
}
export interface TeamForm {
  window: PublicWindow;
  entryFee: number | null;
  currency: string;
  logo: PhotoRules;
  consentVersion: string;
  turnstileSiteKey: string | null;
  serverTime: string;
}
export interface Receipt {
  referenceNumber: string;
  submittedAt: string;
}
export interface UploadedImage {
  id: string;
  width: number;
  height: number;
}

export function usePlayerForm() {
  return useQuery({
    queryKey: ['public', 'registration', 'player-form'],
    queryFn: () => api<PlayerForm>('/public/registration/player/form', { anonymous: true }),
    staleTime: 60_000,
  });
}

export function useTeamForm() {
  return useQuery({
    queryKey: ['public', 'registration', 'team-form'],
    queryFn: () => api<TeamForm>('/public/registration/team/form', { anonymous: true }),
    staleTime: 60_000,
  });
}

interface SessionTicket {
  token: string;
  expiresAt: string;
}
interface UploadTicket {
  assetId: string;
  uploadUrl: string;
  headers: Record<string, string>;
}

const TOKEN_HEADER = 'X-Registration-Token';

/**
 * A short-lived anonymous session scopes uploads to one applicant (SRS §7.2). Kept in memory
 * only; if it expires mid-form the next call starts a fresh one (uploads are then redone).
 */
export class RegistrationSession {
  private ticket: SessionTicket | null = null;

  constructor(private readonly kind: RegistrationKind) {}

  private async token(): Promise<string> {
    const fresh = this.ticket && new Date(this.ticket.expiresAt).getTime() - Date.now() > 60_000;
    if (!fresh) {
      this.ticket = await api<SessionTicket>('/public/registration/sessions', {
        method: 'POST',
        body: { kind: this.kind },
        anonymous: true,
      });
    }
    return this.ticket!.token;
  }

  reset() {
    this.ticket = null;
  }

  async upload(blob: Blob, filename: string, onProgress: (pct: number) => void): Promise<UploadedImage> {
    const headers = { [TOKEN_HEADER]: await this.token() };
    const ticket = await api<UploadTicket>('/public/registration/uploads', {
      method: 'POST',
      anonymous: true,
      headers,
      body: { filename, contentType: blob.type, size: blob.size },
    });
    await putWithProgress(ticket.uploadUrl, blob, ticket.headers, onProgress);
    return api<UploadedImage>(`/public/registration/uploads/${ticket.assetId}/complete`, {
      method: 'POST',
      anonymous: true,
      headers,
    });
  }

  async submit<B extends object>(kind: 'player' | 'team', body: B): Promise<Receipt> {
    if (!this.ticket)
      throw new ApiError(410, {
        status: 410,
        code: 'REGISTRATION_SESSION_EXPIRED',
        detail: 'Add your image again — the form session expired.',
      });
    return api<Receipt>(`/public/registration/${kind}`, {
      method: 'POST',
      anonymous: true,
      headers: { [TOKEN_HEADER]: this.ticket.token },
      body,
    });
  }
}

function putWithProgress(url: string, blob: Blob, headers: Record<string, string>, onProgress: (pct: number) => void) {
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    Object.entries(headers).forEach(([k, v]) => xhr.setRequestHeader(k, v));
    xhr.upload.onprogress = (e) => e.lengthComputable && onProgress(Math.round((e.loaded / e.total) * 100));
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300
        ? resolve()
        : reject(new ApiError(xhr.status, { status: xhr.status, code: 'UPLOAD_FAILED', detail: 'The upload was refused. Try again.' }));
    xhr.onerror = () =>
      reject(new ApiError(0, { status: 0, code: 'UPLOAD_FAILED', detail: 'Upload failed. Check your connection and try again.' }));
    xhr.send(blob);
  });
}

export async function checkTeamName(name: string, signal?: AbortSignal) {
  return api<{ available: boolean }>('/public/registration/team/name-available', { anonymous: true, query: { name }, signal });
}

/** "077 123 4567" or "771234567" → "+94771234567". */
export function toE164Lk(local: string): string {
  const digits = local.replace(/\D/g, '');
  if (!digits) return '';
  if (local.trim().startsWith('+')) return `+${digits}`;
  return `+94${digits.replace(/^0+/, '')}`;
}

/** "+94771234567" → "77 123 4567" for display. */
export function formatLkPhone(e164: string | null | undefined): string {
  if (!e164) return '—';
  const m = /^\+94(\d{2})(\d{3})(\d{4})$/.exec(e164);
  return m ? `+94 ${m[1]} ${m[2]} ${m[3]}` : e164;
}

// ---------------------------------------------------------------------------------------------
// Staff review (lk.ngbcl.registration.web.RegistrationAdminController)
// ---------------------------------------------------------------------------------------------

export type VerificationStatus = FileAssetView['verificationStatus'];
export type DuplicateResolution = 'OPEN' | 'LINKED' | 'NEW_PROFILE' | 'NOT_DUPLICATE';
export type DuplicateAction = 'LINK_EXISTING' | 'NEW_PROFILE' | 'NOT_DUPLICATE';

export interface Blocker {
  code: string;
  message: string;
}
export interface NoteView {
  id: string;
  authorName: string;
  body: string;
  createdAt: string;
}
export interface PlayerRegistrationRow {
  id: string;
  registrationNumber: string;
  fullName: string;
  email: string;
  phone: string;
  homeArea: string | null;
  categoryId: string | null;
  categoryName: string | null;
  status: RegistrationStatus;
  duplicateFlag: boolean;
  photoStatus: VerificationStatus | null;
  photoPreviewUrl: string | null;
  submittedAt: string;
  version: number;
}
export interface PlayerSummary {
  id: string;
  slug: string;
  fullName: string;
  email: string;
  phone: string;
  dateOfBirth: string | null;
  homeArea: string | null;
  photoUrl: string | null;
  inThisEdition: boolean;
}
export interface CandidateRegistration {
  id: string;
  registrationNumber: string;
  fullName: string;
  email: string;
  phone: string;
  dateOfBirth: string | null;
  status: RegistrationStatus;
  submittedAt: string;
}
export interface DuplicateView {
  id: string;
  matchedOn: string;
  resolution: DuplicateResolution;
  resolvedAt: string | null;
  registration: CandidateRegistration | null;
  player: PlayerSummary | null;
}
export interface PlayerRegistrationDetail {
  id: string;
  registrationNumber: string;
  status: RegistrationStatus;
  fullName: string;
  dateOfBirth: string | null;
  gender: string | null;
  email: string;
  phone: string;
  address: string | null;
  homeArea: string | null;
  experience: string | null;
  emergencyName: string | null;
  emergencyPhone: string | null;
  categoryId: string | null;
  categoryName: string | null;
  photo: FileAssetView;
  duplicateFlag: boolean;
  duplicates: DuplicateView[];
  linkedPlayer: PlayerSummary | null;
  playerId: string | null;
  submittedAt: string;
  reviewedAt: string | null;
  rejectionReason: string | null;
  rejectionMessage: string | null;
  notes: NoteView[];
  blockers: Blocker[];
  version: number;
}
export interface TeamRegistrationRow {
  id: string;
  registrationNumber: string;
  teamName: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  status: RegistrationStatus;
  logoStatus: VerificationStatus | null;
  logoPreviewUrl: string | null;
  nameTaken: boolean;
  submittedAt: string;
  version: number;
}
export interface TeamRegistrationDetail {
  id: string;
  registrationNumber: string;
  status: RegistrationStatus;
  teamName: string;
  description: string | null;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  logo: FileAssetView;
  nameTaken: boolean;
  suggestedShortCode: string | null;
  teamId: string | null;
  submittedAt: string;
  reviewedAt: string | null;
  rejectionReason: string | null;
  rejectionMessage: string | null;
  notes: NoteView[];
  blockers: Blocker[];
  version: number;
}
export interface RegistrationSummary {
  pendingPlayers: number;
  pendingTeams: number;
  flaggedPlayers: number;
  approvedPlayers: number;
  approvedTeams: number;
  rejectedPlayers: number;
  rejectedTeams: number;
}
export interface BulkResult {
  approved: string[];
  skipped: { id: string; registrationNumber: string | null; code: string; message: string }[];
}

export const REJECTION_REASONS = [
  'Not eligible (outside Jaffna)',
  'Photo does not meet requirements',
  'Duplicate registration',
  'Incomplete or incorrect details',
  'Other',
] as const;

const regKey = ['admin', 'registrations'] as const;

export interface PlayerInboxFilter {
  status?: RegistrationStatus;
  q?: string;
  duplicate?: boolean;
  categoryId?: string;
  page: number;
  size: number;
}

export function useRegistrationSummary() {
  return useQuery({
    queryKey: [...regKey, 'summary'],
    queryFn: () => api<RegistrationSummary>('/admin/registrations/summary'),
    refetchInterval: 30_000,
  });
}

export function usePlayerRegistrations(f: PlayerInboxFilter) {
  return useQuery({
    queryKey: [...regKey, 'players', f],
    queryFn: () => api<Page<PlayerRegistrationRow>>('/admin/registrations/players', { query: { ...f } }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
}

export function usePlayerRegistration(id: string | null) {
  return useQuery({
    queryKey: [...regKey, 'player', id],
    queryFn: () => api<PlayerRegistrationDetail>(`/admin/registrations/players/${id}`),
    enabled: Boolean(id),
  });
}

/** All review actions return the fresh detail; we write it into the cache and refresh lists. */
function usePlayerAction<V>(path: (v: V) => string, body: (v: V) => unknown, method: 'POST' | 'PATCH' = 'POST') {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: V) => api<PlayerRegistrationDetail>(path(v), { method, body: body(v) }),
    onSuccess: (d) => {
      qc.setQueryData([...regKey, 'player', d.id], d);
      void qc.invalidateQueries({ queryKey: [...regKey, 'players'] });
      void qc.invalidateQueries({ queryKey: [...regKey, 'summary'] });
    },
  });
}

const p = (id: string) => `/admin/registrations/players/${id}`;

export const useVerifyPhoto = () =>
  usePlayerAction<{ id: string; approve: boolean; reason?: string }>(
    (v) => `${p(v.id)}/photo/verify`,
    (v) => ({ approve: v.approve, reason: v.reason }),
  );
export const useResolveDuplicate = () =>
  usePlayerAction<{ id: string; duplicateId: string; action: DuplicateAction }>(
    (v) => `${p(v.id)}/duplicates/${v.duplicateId}/resolve`,
    (v) => ({ action: v.action }),
  );
export const useApprovePlayer = () =>
  usePlayerAction<{ id: string; version: number }>(
    (v) => `${p(v.id)}/approve`,
    (v) => ({ version: v.version }),
  );
export const useRejectPlayer = () =>
  usePlayerAction<{ id: string; reason: string; message?: string; version: number }>(
    (v) => `${p(v.id)}/reject`,
    ({ reason, message, version }) => ({ reason, message, version }),
  );
export const useNotePlayer = () =>
  usePlayerAction<{ id: string; body: string }>(
    (v) => `${p(v.id)}/notes`,
    (v) => ({ body: v.body }),
  );

export interface PlayerEdit {
  fullName: string;
  dateOfBirth: string | null;
  gender: string | null;
  email: string;
  phone: string;
  address: string | null;
  homeArea: string | null;
  experience: string | null;
  emergencyName: string | null;
  emergencyPhone: string | null;
  skillCategoryId: string | null;
  version: number;
}
export const useEditPlayer = () =>
  usePlayerAction<{ id: string } & PlayerEdit>(
    (v) => p(v.id),
    (v) => {
      const { id, ...rest } = v;
      void id;
      return rest;
    },
    'PATCH',
  );

export function useBulkApprove() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ids: string[]) => api<BulkResult>('/admin/registrations/players/bulk-approve', { method: 'POST', body: { ids } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: regKey }),
  });
}

export interface TeamInboxFilter {
  status?: RegistrationStatus;
  q?: string;
  page: number;
  size: number;
}

export function useTeamRegistrations(f: TeamInboxFilter) {
  return useQuery({
    queryKey: [...regKey, 'teams', f],
    queryFn: () => api<Page<TeamRegistrationRow>>('/admin/registrations/teams', { query: { ...f } }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
}

export function useTeamRegistration(id: string | null) {
  return useQuery({
    queryKey: [...regKey, 'team', id],
    queryFn: () => api<TeamRegistrationDetail>(`/admin/registrations/teams/${id}`),
    enabled: Boolean(id),
  });
}

function useTeamAction<V>(path: (v: V) => string, body: (v: V) => unknown) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: V) => api<TeamRegistrationDetail>(path(v), { method: 'POST', body: body(v) }),
    onSuccess: (d) => {
      qc.setQueryData([...regKey, 'team', d.id], d);
      void qc.invalidateQueries({ queryKey: [...regKey, 'teams'] });
      void qc.invalidateQueries({ queryKey: [...regKey, 'summary'] });
      void qc.invalidateQueries({ queryKey: ['admin', 'teams'] });
    },
  });
}

const t = (id: string) => `/admin/registrations/teams/${id}`;

export const useVerifyLogo = () =>
  useTeamAction<{ id: string; approve: boolean; reason?: string }>(
    (v) => `${t(v.id)}/logo/verify`,
    (v) => ({ approve: v.approve, reason: v.reason }),
  );
export const useApproveTeam = () =>
  useTeamAction<{ id: string; shortCode?: string; colorSlot?: number | null; version: number }>(
    (v) => `${t(v.id)}/approve`,
    ({ shortCode, colorSlot, version }) => ({ shortCode: shortCode || undefined, colorSlot: colorSlot ?? undefined, version }),
  );
export const useRejectTeam = () =>
  useTeamAction<{ id: string; reason: string; message?: string; version: number }>(
    (v) => `${t(v.id)}/reject`,
    ({ reason, message, version }) => ({ reason, message, version }),
  );
export const useNoteTeam = () =>
  useTeamAction<{ id: string; body: string }>(
    (v) => `${t(v.id)}/notes`,
    (v) => ({ body: v.body }),
  );
