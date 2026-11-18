'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type { SponsorTier } from './public';

// Admin › Content (lk.ngbcl.content.web.ContentAdminController)

export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface Announcement {
  id: string;
  title: string;
  slug: string;
  body: string;
  coverAssetId: string | null;
  coverUrl: string | null;
  status: AnnouncementStatus;
  displayStatus: AnnouncementStatus | 'SCHEDULED';
  pinned: boolean;
  publishAt: string | null;
  authorName: string | null;
  updatedAt: string;
  version: number;
}

export interface AnnouncementInput {
  title: string;
  body: string;
  coverAssetId: string | null;
  pinned: boolean;
  status: AnnouncementStatus;
  publishAt: string | null;
}

const newsKey = ['admin', 'content', 'announcements'] as const;

export function useAnnouncements() {
  return useQuery({ queryKey: newsKey, queryFn: () => api<Announcement[]>('/admin/content/announcements') });
}

export function useSaveAnnouncement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version, ...b }: AnnouncementInput & { id?: string; version?: number }) =>
      id
        ? api<Announcement>(`/admin/content/announcements/${id}`, { method: 'PATCH', body: { ...b, version } })
        : api<Announcement>('/admin/content/announcements', { method: 'POST', body: b }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: newsKey }),
  });
}

export interface Sponsor {
  id: string;
  name: string;
  tier: SponsorTier;
  logoAssetId: string | null;
  logoUrl: string | null;
  websiteUrl: string | null;
  awardName: string | null;
  showOnWall: boolean;
  showOnTicker: boolean;
  active: boolean;
  displayOrder: number;
  version: number;
}

export type SponsorInput = Omit<Sponsor, 'id' | 'logoUrl' | 'displayOrder' | 'version'>;

export const SPONSOR_TIERS: { value: SponsorTier; label: string }[] = [
  { value: 'TITLE', label: 'Title' },
  { value: 'PLATINUM', label: 'Platinum' },
  { value: 'GOLD', label: 'Gold' },
  { value: 'SILVER', label: 'Silver' },
  { value: 'BRONZE', label: 'Bronze' },
  { value: 'MEDIA', label: 'Media partner' },
];

const sponsorsKey = ['admin', 'content', 'sponsors'] as const;

export function useSponsors() {
  return useQuery({ queryKey: sponsorsKey, queryFn: () => api<Sponsor[]>('/admin/content/sponsors') });
}

export function useSaveSponsor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, version, ...b }: SponsorInput & { id?: string; version?: number }) =>
      id
        ? api<Sponsor>(`/admin/content/sponsors/${id}`, { method: 'PATCH', body: { ...b, version } })
        : api<Sponsor>('/admin/content/sponsors', { method: 'POST', body: b }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: sponsorsKey }),
  });
}

export function useReorderSponsors() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ids: string[]) => api<Sponsor[]>('/admin/content/sponsors/order', { method: 'PUT', body: { ids } }),
    onSuccess: (data) => qc.setQueryData(sponsorsKey, data),
  });
}

export type CommitteeStyle = 'LEAD' | 'COMPACT';

export interface CommitteeMember {
  id: string;
  groupId: string;
  fullName: string;
  role: string;
  bio: string | null;
  photoAssetId: string | null;
  photoUrl: string | null;
  visible: boolean;
  displayOrder: number;
  version: number;
}

export interface CommitteeGroup {
  id: string;
  name: string;
  style: CommitteeStyle;
  displayOrder: number;
  members: CommitteeMember[];
  version: number;
}

export interface MemberInput {
  groupId: string;
  fullName: string;
  role: string;
  bio: string | null;
  photoAssetId: string | null;
  visible: boolean;
}

const committeeKey = ['admin', 'content', 'committee'] as const;

export function useCommittee() {
  return useQuery({ queryKey: committeeKey, queryFn: () => api<CommitteeGroup[]>('/admin/content/committee') });
}

/** Every committee mutation returns the whole list, which replaces the cache. */
function useCommitteeMutation<V>(fn: (v: V) => Promise<CommitteeGroup[]>) {
  const qc = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: (data) => qc.setQueryData(committeeKey, data) });
}

const c = '/admin/content/committee';

export const useSaveGroup = () =>
  useCommitteeMutation(({ id, ...b }: { id?: string; name: string; style: CommitteeStyle; version?: number }) =>
    id
      ? api<CommitteeGroup[]>(`${c}/groups/${id}`, { method: 'PATCH', body: b })
      : api<CommitteeGroup[]>(`${c}/groups`, { method: 'POST', body: b }),
  );
export const useDeleteGroup = () => useCommitteeMutation((id: string) => api<CommitteeGroup[]>(`${c}/groups/${id}`, { method: 'DELETE' }));
export const useReorderGroups = () =>
  useCommitteeMutation((ids: string[]) => api<CommitteeGroup[]>(`${c}/groups/order`, { method: 'PUT', body: { ids } }));
export const useReorderMembers = () =>
  useCommitteeMutation(({ groupId, ids }: { groupId: string; ids: string[] }) =>
    api<CommitteeGroup[]>(`${c}/groups/${groupId}/members/order`, { method: 'PUT', body: { ids } }),
  );
export const useSaveMember = () =>
  useCommitteeMutation(({ id, version, ...b }: MemberInput & { id?: string; version?: number }) =>
    id
      ? api<CommitteeGroup[]>(`${c}/members/${id}`, { method: 'PATCH', body: { ...b, version } })
      : api<CommitteeGroup[]>(`${c}/members`, { method: 'POST', body: b }),
  );
export const useDeleteMember = () =>
  useCommitteeMutation((id: string) => api<CommitteeGroup[]>(`${c}/members/${id}`, { method: 'DELETE' }));
