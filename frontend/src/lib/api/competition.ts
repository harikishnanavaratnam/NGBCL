'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';

// Competition (lk.ngbcl.competition.web.CompetitionController)

export type CompetitionFormat = 'GROUPS_KNOCKOUT' | 'ROUND_ROBIN' | 'ROUND_ROBIN_FINAL' | 'KNOCKOUT';
export type GenderRule = 'ANY' | 'MALE' | 'FEMALE' | 'MIXED';
export type StageType = 'GROUP' | 'LEAGUE' | 'KNOCKOUT';
export type FixtureStatus = 'DRAFT' | 'SCHEDULED' | 'LIVE' | 'COMPLETED' | 'CANCELLED';
export type MatchStatus = 'SCHEDULED' | 'LIVE' | 'SUBMITTED' | 'COMPLETED' | 'CANCELLED';

export interface CompetitionSettings {
  format: CompetitionFormat;
  groupCount: number;
  qualifiersPerGroup: number;
  thirdPlaceMatch: boolean;
  maxMatchesPerPlayer: number;
  matchMinutes: number;
  restMinutes: number;
  version: number | null;
}

export interface MatchCategory {
  id: string;
  code: string;
  name: string;
  playersPerSide: 1 | 2;
  genderRule: GenderRule;
  active: boolean;
  inUse: boolean;
  version: number;
}

export interface TemplateItem {
  position: number;
  categoryId: string;
  code: string;
  name: string;
}

export interface Court {
  id: string;
  name: string;
  active: boolean;
  version: number;
}

export interface CompetitionSetup {
  settings: CompetitionSettings;
  categories: MatchCategory[];
  template: TemplateItem[];
  courts: Court[];
  fixturesGenerated: boolean;
  fixturesPublished: boolean;
}

export interface TeamRef {
  id: string;
  slug: string;
  name: string;
  shortCode: string;
  colorSlot: number | null;
  logoUrl: string | null;
}

export interface Group {
  id: string;
  name: string;
  teams: TeamRef[];
}

export interface Groups {
  groups: Group[];
  unassigned: TeamRef[];
  groupCount: number;
  /** Fixtures exist, so the draw can't change. */
  locked: boolean;
}

export interface PlayerRef {
  entryId: string;
  slug: string | null;
  fullName: string;
  gender: string | null;
}

export interface StaffRef {
  id: string;
  displayName: string;
}

export interface MatchView {
  id: string;
  order: number;
  categoryId: string;
  categoryCode: string;
  categoryName: string;
  playersPerSide: 1 | 2;
  genderRule: GenderRule;
  scheduledAt: string | null;
  sideA: PlayerRef[];
  sideB: PlayerRef[];
  scorer: StaffRef | null;
  status: MatchStatus;
  lineupComplete: boolean;
  /** Set once the match starts or when an organiser overrides the format; null = tournament default. */
  pointsToWin: number | null;
  winBy: number | null;
  pointCap: number | null;
  gamesToWin: number | null;
  rulesCustom: boolean;
  gamesA: number;
  gamesB: number;
  winnerSide: 'A' | 'B' | null;
  outcome: 'NORMAL' | 'WALKOVER' | 'RETIRED' | null;
  gameScores: string[];
}

export interface Fixture {
  id: string;
  number: number;
  stageId: string;
  stageName: string;
  stageType: StageType;
  groupId: string | null;
  groupName: string | null;
  roundNumber: number;
  roundName: string;
  bracketPosition: number | null;
  teamA: TeamRef | null;
  teamB: TeamRef | null;
  sourceA: string | null;
  sourceB: string | null;
  scheduledAt: string | null;
  endsAt: string | null;
  courtId: string | null;
  courtName: string | null;
  status: FixtureStatus;
  published: boolean;
  changedSincePublish: boolean;
  winnerToFixtureId: string | null;
  winnerToSide: 'A' | 'B' | null;
  loserToFixtureId: string | null;
  loserToSide: 'A' | 'B' | null;
  matches: MatchView[];
  lineupsComplete: boolean;
  warnings: string[];
  version: number;
  matchesWonA: number;
  matchesWonB: number;
  result: 'A' | 'B' | 'DRAW' | null;
}

export interface Stage {
  id: string;
  name: string;
  type: StageType;
  order: number;
}

export interface FixturesView {
  stages: Stage[];
  groups: Group[];
  fixtures: Fixture[];
  courts: Court[];
  scorers: StaffRef[];
  matchMinutes: number;
  restMinutes: number;
  unpublished: number;
  changed: number;
}

export interface Roster {
  team: TeamRef;
  players: PlayerRef[];
}

export interface PublishPreview {
  items: { fixtureId: string; number: number; label: string; change: 'NEW' | 'CHANGED' }[];
  unscheduled: number;
}

export const FORMAT_LABEL: Record<CompetitionFormat, string> = {
  GROUPS_KNOCKOUT: 'Groups, then knockout',
  ROUND_ROBIN: 'Round robin (league only)',
  ROUND_ROBIN_FINAL: 'Round robin, then a final',
  KNOCKOUT: 'Knockout only',
};

export const GENDER_LABEL: Record<GenderRule, string> = {
  ANY: 'Anyone',
  MALE: 'Men only',
  FEMALE: 'Women only',
  MIXED: 'One man, one woman',
};

// ---- Hooks ------------------------------------------------------------------------------------

const setupKey = ['competition', 'setup'] as const;
const groupsKey = ['competition', 'groups'] as const;
export const fixturesKey = ['competition', 'fixtures'] as const;

export function useCompetitionSetup() {
  return useQuery({ queryKey: setupKey, queryFn: () => api<CompetitionSetup>('/admin/competition/setup') });
}

function useSetupMutation<V>(fn: (v: V) => Promise<CompetitionSetup>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (s) => {
      qc.setQueryData(setupKey, s);
      void qc.invalidateQueries({ queryKey: groupsKey });
    },
  });
}

export function useSetupCommands() {
  return {
    settings: useSetupMutation((v: Omit<CompetitionSettings, never>) => api<CompetitionSetup>('/admin/competition/settings', { method: 'PUT', body: v })),
    category: useSetupMutation(({ id, ...body }: { id?: string; code: string; name: string; playersPerSide: number; genderRule: GenderRule; active?: boolean; version?: number }) =>
      id
        ? api<CompetitionSetup>(`/admin/competition/categories/${id}`, { method: 'PUT', body })
        : api<CompetitionSetup>('/admin/competition/categories', { method: 'POST', body }),
    ),
    template: useSetupMutation((categoryIds: string[]) => api<CompetitionSetup>('/admin/competition/template', { method: 'PUT', body: { categoryIds } })),
    court: useSetupMutation(({ id, ...body }: { id?: string; name: string; active?: boolean; version?: number }) =>
      id ? api<CompetitionSetup>(`/admin/competition/courts/${id}`, { method: 'PUT', body }) : api<CompetitionSetup>('/admin/competition/courts', { method: 'POST', body }),
    ),
  };
}

export function useGroups() {
  return useQuery({ queryKey: groupsKey, queryFn: () => api<Groups>('/admin/competition/groups') });
}

export function useDrawGroups() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (teamIds: string[]) => api<Groups>('/admin/competition/groups/draw', { method: 'POST', body: { teamIds } }),
    onSuccess: (g) => qc.setQueryData(groupsKey, g),
  });
}

export function useSaveGroups() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (groups: string[][]) => api<Groups>('/admin/competition/groups', { method: 'PUT', body: { groups } }),
    onSuccess: (g) => qc.setQueryData(groupsKey, g),
  });
}

export function useFixtures() {
  return useQuery({ queryKey: fixturesKey, queryFn: () => api<FixturesView>('/admin/competition/fixtures') });
}

function useFixturesMutation<V>(fn: (v: V) => Promise<FixturesView>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (v) => {
      qc.setQueryData(fixturesKey, v);
      void qc.invalidateQueries({ queryKey: setupKey });
      void qc.invalidateQueries({ queryKey: groupsKey });
    },
  });
}

export function useGenerateFixtures() {
  return useFixturesMutation((seedOrder: string[] | undefined) =>
    api<FixturesView>('/admin/competition/fixtures/generate', { method: 'POST', body: seedOrder ? { seedOrder } : {} }),
  );
}

export function useDeleteFixtures() {
  return useFixturesMutation(() => api<FixturesView>('/admin/competition/fixtures', { method: 'DELETE' }));
}

/** Replaces one fixture inside the cached list after an edit. */
function useFixtureMutation<V>(fn: (v: V) => Promise<Fixture>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (f) => {
      qc.setQueryData<FixturesView>(fixturesKey, (old) => {
        if (!old) return old;
        const fixtures = old.fixtures.map((x) => (x.id === f.id ? f : x));
        return {
          ...old,
          fixtures,
          unpublished: fixtures.filter((x) => !x.published).length,
          changed: fixtures.filter((x) => x.published && x.changedSincePublish).length,
        };
      });
    },
    onError: () => void qc.invalidateQueries({ queryKey: fixturesKey }),
  });
}

export function useFixtureCommands() {
  return {
    schedule: useFixtureMutation((v: { id: string; scheduledAt: string | null; courtId: string | null; version?: number }) =>
      api<Fixture>(`/admin/competition/fixtures/${v.id}/schedule`, { method: 'PUT', body: { scheduledAt: v.scheduledAt, courtId: v.courtId, version: v.version } }),
    ),
    teams: useFixtureMutation((v: { id: string; teamAId: string | null; teamBId: string | null; version?: number }) =>
      api<Fixture>(`/admin/competition/fixtures/${v.id}/teams`, { method: 'PUT', body: { teamAId: v.teamAId, teamBId: v.teamBId, version: v.version } }),
    ),
    lineup: useFixtureMutation((v: { id: string; matchId: string; sideA: string[]; sideB: string[]; scorerUserId: string | null; version?: number }) =>
      api<Fixture>(`/admin/competition/fixtures/${v.id}/matches/${v.matchId}/lineup`, {
        method: 'PUT',
        body: { sideA: v.sideA, sideB: v.sideB, scorerUserId: v.scorerUserId, version: v.version },
      }),
    ),
  };
}

export function useRosters(fixtureId: string | null) {
  return useQuery({
    queryKey: ['competition', 'rosters', fixtureId],
    queryFn: () => api<(Roster | null)[]>(`/admin/competition/fixtures/${fixtureId}/rosters`),
    enabled: Boolean(fixtureId),
  });
}

export function usePublishPreview(enabled: boolean) {
  return useQuery({
    queryKey: ['competition', 'publish-preview'],
    queryFn: () => api<PublishPreview>('/admin/competition/fixtures/publish-preview'),
    enabled,
  });
}

export function usePublishFixtures() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (fixtureIds?: string[]) =>
      api<{ published: number; view: FixturesView }>('/admin/competition/fixtures/publish', { method: 'POST', body: fixtureIds ? { fixtureIds } : {} }),
    onSuccess: (r) => {
      qc.setQueryData(fixturesKey, r.view);
      void qc.invalidateQueries({ queryKey: ['competition', 'publish-preview'] });
    },
  });
}

/** Display label for an undecided knockout slot. */
export function sideLabel(team: TeamRef | null, source: string | null) {
  return team?.name ?? source ?? 'To be decided';
}
