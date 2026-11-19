'use client';

import clsx from 'clsx';
import { useMemo, useState } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select, Toggle } from '@/design-system/primitives/Inputs';
import { Drawer, Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { Segmented } from '@/design-system/primitives/Tabs';
import { useToast } from '@/design-system/primitives/Toast';
import { CrestTile } from '@/design-system/sport/CrestTile';
import {
  GENDER_LABEL,
  sideLabel,
  useFixtureCommands,
  useFixtures,
  useGroups,
  usePublishFixtures,
  usePublishPreview,
  useRosters,
  type Fixture,
  type FixturesView,
  type MatchView,
  type PlayerRef,
  type TeamRef,
} from '@/lib/api/competition';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { useMatchRules, useScoringSettings } from '@/lib/api/scoring';
import { GameScoreTiles } from '@/design-system/sport/Scores';
import { rulesSummary } from '../scoring/ScoringSettingsPanel';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime, fromZonedInput, toZonedInput } from '@/lib/format/dates';
import s from './Competition.module.css';

type View = 'list' | 'bracket' | 'table';

/** Admin › Competition › Fixtures: list / bracket / cross-table, tie editor, publish with diff. */
export function FixturesAdmin() {
  const { data, isLoading, error, refetch } = useFixtures();
  const [view, setView] = useState<View>('list');
  const [stage, setStage] = useState('');
  const [team, setTeam] = useState('');
  const [attention, setAttention] = useState(false);
  const [openId, setOpenId] = useState<string | null>(null);
  const [publishOpen, setPublishOpen] = useState(false);
  const { can } = useAuth();
  const canEdit = can('FIXTURE_MANAGE');

  if (isLoading) {
    return (
      <div className={s.page} aria-busy="true">
        <Skeleton height={40} width={280} />
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} height={64} />
        ))}
      </div>
    );
  }
  if (error || !data) return <ErrorState title="Couldn’t load fixtures" message={errorMessage(error)} onRetry={() => void refetch()} />;

  const teams = uniqueTeams(data);
  const list = data.fixtures.filter(
    (f) =>
      (!stage || f.stageId === stage) &&
      (!team || f.teamA?.id === team || f.teamB?.id === team) &&
      (!attention || f.warnings.length > 0 || f.changedSincePublish),
  );
  const pending = data.unpublished + data.changed;
  const open = data.fixtures.find((f) => f.id === openId) ?? null;

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <nav aria-label="Breadcrumb" className="t-caption">
            Competition / Fixtures
          </nav>
          <h1 className="t-h1">Fixtures</h1>
          <p className="muted t-body-s" style={{ margin: 0 }}>
            {data.fixtures.length} ties · {data.fixtures.filter((f) => f.scheduledAt).length} scheduled ·{' '}
            {data.fixtures.filter((f) => f.lineupsComplete).length} with full line-ups
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button href="/admin/competition/schedule" variant="out">
            Schedule board
          </Button>
          {canEdit && (
            <Button variant="volt" disabled={pending === 0} onClick={() => setPublishOpen(true)}>
              {pending === 0 ? 'All published' : `Publish ${pending} change${pending === 1 ? '' : 's'}`}
            </Button>
          )}
        </div>
      </div>

      {data.fixtures.length === 0 ? (
        <EmptyState
          title="No fixtures yet"
          description="Choose the format and draw the groups, then generate the ties."
          action={{ label: 'Format and draw', href: '/admin/competition/format' }}
        />
      ) : (
        <>
          <div className={s.toolbar}>
            <Segmented
              label="View"
              value={view}
              onChange={setView}
              options={[
                { value: 'list', label: 'List' },
                { value: 'bracket', label: 'Bracket' },
                { value: 'table', label: 'Cross-table' },
              ]}
            />
            {view === 'list' && (
              <>
                <Select aria-label="Stage" value={stage} onChange={(e) => setStage(e.target.value)} style={{ maxWidth: 200 }}>
                  <option value="">All stages</option>
                  {data.stages.map((x) => (
                    <option key={x.id} value={x.id}>
                      {x.name}
                    </option>
                  ))}
                </Select>
                <Select aria-label="Team" value={team} onChange={(e) => setTeam(e.target.value)} style={{ maxWidth: 220 }}>
                  <option value="">All teams</option>
                  {teams.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </Select>
                <Toggle id="attn" label="Needs attention" checked={attention} onChange={(e) => setAttention(e.target.checked)} />
              </>
            )}
          </div>
          {view === 'list' && <FixtureList fixtures={list} onOpen={setOpenId} />}
          {view === 'bracket' && <Bracket data={data} onOpen={setOpenId} />}
          {view === 'table' && <CrossTables data={data} onOpen={setOpenId} />}
        </>
      )}

      <Drawer
        open={open !== null}
        onClose={() => setOpenId(null)}
        size={720}
        title={open ? `Tie #${open.number} · ${open.roundName}` : 'Tie'}
      >
        {open && <TieEditor key={open.id} fixture={open} data={data} canEdit={canEdit} />}
      </Drawer>
      {publishOpen && <PublishDialog onClose={() => setPublishOpen(false)} />}
    </div>
  );
}

function uniqueTeams(data: FixturesView): TeamRef[] {
  const m = new Map<string, TeamRef>();
  data.fixtures.forEach((f) => [f.teamA, f.teamB].forEach((t) => t && m.set(t.id, t)));
  return [...m.values()].sort((a, b) => a.name.localeCompare(b.name));
}

function FixtureList({ fixtures, onOpen }: { fixtures: Fixture[]; onOpen: (id: string) => void }) {
  const rounds = useMemo(() => {
    const m = new Map<string, Fixture[]>();
    fixtures.forEach((f) => {
      const k = `${f.stageName} · ${f.roundName}`;
      m.set(k, [...(m.get(k) ?? []), f]);
    });
    return [...m.entries()];
  }, [fixtures]);
  if (fixtures.length === 0) return <p className="muted">No ties match these filters.</p>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {rounds.map(([name, fs]) => (
        <section key={name} className={s.round} aria-label={name}>
          <div className={s.roundHead}>
            <h2 className="t-h3">{name}</h2>
            <span className="t-caption">
              {fs.length} {fs.length === 1 ? 'tie' : 'ties'}
            </span>
          </div>
          {fs.map((f) => (
            <TieRow key={f.id} f={f} onOpen={onOpen} />
          ))}
        </section>
      ))}
    </div>
  );
}

function TieRow({ f, onOpen }: { f: Fixture; onOpen: (id: string) => void }) {
  return (
    <button
      type="button"
      className={s.tie}
      onClick={() => onOpen(f.id)}
      aria-label={`Tie ${f.number}: ${sideLabel(f.teamA, f.sourceA)} v ${sideLabel(f.teamB, f.sourceB)}`}
    >
      <span className="mono muted">#{f.number}</span>
      <span className={s.vs}>
        <Side team={f.teamA} source={f.sourceA} />
        {f.status === 'SCHEDULED' ? (
          <span className="muted mono" style={{ fontSize: 12 }}>
            v
          </span>
        ) : (
          <span className="num" style={{ fontSize: 18 }} aria-label={`${f.matchesWonA} matches to ${f.matchesWonB}`}>
            {f.matchesWonA}–{f.matchesWonB}
          </span>
        )}
        <Side team={f.teamB} source={f.sourceB} right />
      </span>
      <span className="t-body-s">
        {f.scheduledAt ? (
          <>
            {formatDateTime(f.scheduledAt)}
            <br />
            <span className="muted">{f.courtName}</span>
          </>
        ) : (
          <span className="muted">Not scheduled</span>
        )}
      </span>
      <span className={s.chips}>
        <FixtureChips f={f} />
      </span>
    </button>
  );
}

function FixtureChips({ f }: { f: Fixture }) {
  return (
    <>
      {f.status === 'LIVE' && <StatusChip tone="live">Live</StatusChip>}
      {f.status === 'COMPLETED' && <StatusChip tone="navy">{f.result === 'DRAW' ? 'Drawn' : 'Final'}</StatusChip>}
      {!f.published ? (
        <StatusChip tone="neutral">Draft</StatusChip>
      ) : f.changedSincePublish ? (
        <StatusChip tone="pending">Changed</StatusChip>
      ) : (
        <StatusChip tone="ok">Published</StatusChip>
      )}
      {f.warnings
        .filter((w) => w !== 'Not scheduled')
        .map((w) => (
          <span key={w} className="chip c-pend">
            {w}
          </span>
        ))}
    </>
  );
}

function Side({ team, source, right }: { team: TeamRef | null; source: string | null; right?: boolean }) {
  return (
    <span className={clsx(s.side, right && s.sideB)}>
      {team ? (
        <>
          {!right && <CrestTile logoUrl={team.logoUrl} name={team.name} colorSlot={team.colorSlot} size="s" />}
          <span>{team.name}</span>
          {right && <CrestTile logoUrl={team.logoUrl} name={team.name} colorSlot={team.colorSlot} size="s" />}
        </>
      ) : (
        <span className={s.tbd}>{source ?? 'To be decided'}</span>
      )}
    </span>
  );
}

// ---- Bracket and cross-table -------------------------------------------------------------------

function Bracket({ data, onOpen }: { data: FixturesView; onOpen: (id: string) => void }) {
  const ko = data.fixtures.filter((f) => f.stageType === 'KNOCKOUT');
  if (ko.length === 0) return <p className="muted">This format has no knockout stage.</p>;
  const main = ko.filter((f) => f.roundName !== '3rd place');
  const third = ko.find((f) => f.roundName === '3rd place');
  const rounds = [...new Set(main.map((f) => f.roundNumber))].sort((a, b) => a - b);
  return (
    <div className={s.bracket}>
      {rounds.map((r) => {
        const fs = main.filter((f) => f.roundNumber === r).sort((a, b) => (a.bracketPosition ?? 0) - (b.bracketPosition ?? 0));
        return (
          <div key={r} className={s.bracketCol}>
            <span className="eyebrow">{fs[0]?.roundName}</span>
            {fs.map((f) => (
              <BracketTie key={f.id} f={f} onOpen={onOpen} />
            ))}
            {r === rounds[rounds.length - 1] && third && (
              <>
                <span className="eyebrow" style={{ marginTop: 16 }}>
                  3rd place
                </span>
                <BracketTie f={third} onOpen={onOpen} />
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

function BracketTie({ f, onOpen }: { f: Fixture; onOpen: (id: string) => void }) {
  return (
    <button type="button" className={s.bracketTie} onClick={() => onOpen(f.id)}>
      <div>
        <Side team={f.teamA} source={f.sourceA} />
      </div>
      <div>
        <Side team={f.teamB} source={f.sourceB} />
      </div>
      <div className="t-caption" style={{ justifyContent: 'space-between' }}>
        <span>#{f.number}</span>
        <span>{f.scheduledAt ? formatDateTime(f.scheduledAt) : 'Not scheduled'}</span>
      </div>
    </button>
  );
}

function CrossTables({ data, onOpen }: { data: FixturesView; onOpen: (id: string) => void }) {
  const pools =
    data.groups.length > 0
      ? data.groups.map((g) => ({ name: g.name, teams: g.teams, fixtures: data.fixtures.filter((f) => f.groupId === g.id) }))
      : [
          {
            name: 'League',
            teams: uniqueTeams({ ...data, fixtures: data.fixtures.filter((f) => f.stageType === 'LEAGUE') }),
            fixtures: data.fixtures.filter((f) => f.stageType === 'LEAGUE'),
          },
        ];
  if (pools.every((p) => p.fixtures.length === 0)) return <p className="muted">This format has no group or league stage.</p>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {pools.map((p) => (
        <section key={p.name} className="card scroll" style={{ padding: 16, overflowX: 'auto' }} aria-label={p.name}>
          <h2 className="t-h3" style={{ marginBottom: 12 }}>
            {p.name}
          </h2>
          <table className={clsx('tbl', s.cross)}>
            <thead>
              <tr>
                <th />
                {p.teams.map((t) => (
                  <th key={t.id}>{t.shortCode}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {p.teams.map((row) => (
                <tr key={row.id}>
                  <th style={{ textAlign: 'left' }}>{row.name}</th>
                  {p.teams.map((col) => {
                    if (row.id === col.id) return <td key={col.id} className={s.self} aria-label="Same team" />;
                    const f = p.fixtures.find(
                      (x) => (x.teamA?.id === row.id && x.teamB?.id === col.id) || (x.teamA?.id === col.id && x.teamB?.id === row.id),
                    );
                    return (
                      <td key={col.id}>
                        {f ? (
                          <button
                            type="button"
                            className="link"
                            style={{ background: 'none', border: 0, color: 'inherit', cursor: 'pointer', font: 'inherit' }}
                            onClick={() => onOpen(f.id)}
                          >
                            #{f.number}
                            <br />
                            <span className="t-caption">{f.scheduledAt ? formatDateTime(f.scheduledAt).split(' · ')[0] : 'TBC'}</span>
                          </button>
                        ) : (
                          '—'
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ))}
    </div>
  );
}

// ---- Tie editor ----------------------------------------------------------------------------------

function TieEditor({ fixture: f, data, canEdit }: { fixture: Fixture; data: FixturesView; canEdit: boolean }) {
  const cmd = useFixtureCommands();
  const toast = useToast();
  const rosters = useRosters(f.teamA || f.teamB ? f.id : null);
  const groups = useGroups();
  const [when, setWhen] = useState(toZonedInput(f.scheduledAt));
  const [court, setCourt] = useState(f.courtId ?? '');
  const [teamA, setTeamA] = useState(f.teamA?.id ?? '');
  const [teamB, setTeamB] = useState(f.teamB?.id ?? '');
  const locked = f.status === 'LIVE' || f.status === 'COMPLETED';
  const allTeams = groups.data
    ? [...groups.data.groups.flatMap((g) => g.teams), ...groups.data.unassigned].sort((a, b) => a.name.localeCompare(b.name))
    : [];
  const ko = f.stageType === 'KNOCKOUT';

  const saveSchedule = (clear?: boolean) =>
    cmd.schedule.mutate(
      { id: f.id, scheduledAt: clear ? null : fromZonedInput(when), courtId: clear ? null : court || null, version: f.version },
      {
        onSuccess: () => {
          if (clear) {
            setWhen('');
            setCourt('');
          }
          toast.success(clear ? 'Schedule cleared' : 'Tie scheduled');
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    );

  return (
    <div className={s.editor}>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <span className="t-caption">
          {f.stageName}
          {f.groupName ? ` · ${f.groupName}` : ''}
        </span>
        <FixtureChips f={f} />
      </div>
      <div className={s.vs} style={{ fontSize: 18 }}>
        <Side team={f.teamA} source={f.sourceA} />
        <span className="muted">v</span>
        <Side team={f.teamB} source={f.sourceB} right />
      </div>

      {ko && canEdit && !locked && (
        <section className={s.matchCard} aria-label="Teams">
          <strong>Teams</strong>
          <span className="help">
            Knockout slots fill automatically from results once scoring is live. Until then you can set them here.
          </span>
          <div className={s.lineup}>
            <Field label={`Side A${f.sourceA ? ` (${f.sourceA})` : ''}`}>
              {({ id }) => (
                <Select id={id} value={teamA} onChange={(e) => setTeamA(e.target.value)}>
                  <option value="">To be decided</option>
                  {allTeams.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label={`Side B${f.sourceB ? ` (${f.sourceB})` : ''}`}>
              {({ id }) => (
                <Select id={id} value={teamB} onChange={(e) => setTeamB(e.target.value)}>
                  <option value="">To be decided</option>
                  {allTeams.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          </div>
          <div>
            <Button
              variant="out"
              size="sm"
              loading={cmd.teams.isPending}
              disabled={teamA === (f.teamA?.id ?? '') && teamB === (f.teamB?.id ?? '')}
              onClick={() =>
                cmd.teams.mutate(
                  { id: f.id, teamAId: teamA || null, teamBId: teamB || null, version: f.version },
                  { onSuccess: () => toast.success('Teams saved'), onError: (e) => toast.error(errorMessage(e)) },
                )
              }
            >
              Save teams
            </Button>
          </div>
        </section>
      )}

      <section className={s.matchCard} aria-label="Schedule">
        <strong>Schedule</strong>
        <div className={s.lineup}>
          <Field label="Start (Asia/Colombo)">
            {({ id }) => (
              <Input id={id} type="datetime-local" value={when} disabled={!canEdit || locked} onChange={(e) => setWhen(e.target.value)} />
            )}
          </Field>
          <Field label="Court">
            {({ id }) => (
              <Select id={id} value={court} disabled={!canEdit || locked} onChange={(e) => setCourt(e.target.value)}>
                <option value="">Choose a court</option>
                {data.courts
                  .filter((c) => c.active || c.id === court)
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
              </Select>
            )}
          </Field>
        </div>
        <span className="help">
          {f.matches.length} matches back to back · {data.matchMinutes} min each{data.restMinutes ? ` + ${data.restMinutes} min break` : ''}
          {f.endsAt ? ` · ends about ${formatDateTime(f.endsAt).split(' · ')[1]}` : ''}
        </span>
        {canEdit && !locked && (
          <div style={{ display: 'flex', gap: 8 }}>
            <Button size="sm" variant="volt" disabled={!when || !court} loading={cmd.schedule.isPending} onClick={() => saveSchedule()}>
              Save schedule
            </Button>
            {f.scheduledAt && (
              <Button size="sm" variant="ghost" onClick={() => saveSchedule(true)}>
                Clear
              </Button>
            )}
          </div>
        )}
      </section>

      <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }} aria-label="Matches">
        <strong>Matches and line-ups</strong>
        {!f.teamA || !f.teamB ? (
          <p className="muted" style={{ margin: 0 }}>
            Line-ups can be picked once both teams are known.
          </p>
        ) : rosters.isLoading ? (
          <Skeleton height={160} />
        ) : (
          f.matches.map((m) => (
            <MatchEditor
              key={`${m.id}-${f.version}`}
              f={f}
              m={m}
              squadA={rosters.data?.[0]?.players ?? []}
              squadB={rosters.data?.[1]?.players ?? []}
              scorers={data.scorers}
              canEdit={canEdit && !locked}
              canSetRules={canEdit}
            />
          ))
        )}
      </section>
    </div>
  );
}

function eligible(players: PlayerRef[], rule: MatchView['genderRule'], position: number) {
  if (rule === 'MALE') return players.filter((p) => p.gender === 'MALE');
  if (rule === 'FEMALE') return players.filter((p) => p.gender === 'FEMALE');
  if (rule === 'MIXED') return players.filter((p) => p.gender === (position === 0 ? 'MALE' : 'FEMALE'));
  return players;
}

function MatchEditor({
  f,
  m,
  squadA,
  squadB,
  scorers,
  canEdit: canEditTie,
  canSetRules,
}: {
  f: Fixture;
  m: MatchView;
  squadA: PlayerRef[];
  squadB: PlayerRef[];
  scorers: FixturesView['scorers'];
  canEdit: boolean;
  canSetRules: boolean;
}) {
  const canEdit = canEditTie && m.status === 'SCHEDULED';
  const cmd = useFixtureCommands();
  const toast = useToast();
  const [a, setA] = useState<string[]>(
    pad(
      m.sideA.map((p) => p.entryId),
      m.playersPerSide,
    ),
  );
  const [b, setB] = useState<string[]>(
    pad(
      m.sideB.map((p) => p.entryId),
      m.playersPerSide,
    ),
  );
  const [scorer, setScorer] = useState(m.scorer?.id ?? '');
  const [err, setErr] = useState<Record<string, string>>({});
  const dirty =
    a.join() !==
      pad(
        m.sideA.map((p) => p.entryId),
        m.playersPerSide,
      ).join() ||
    b.join() !==
      pad(
        m.sideB.map((p) => p.entryId),
        m.playersPerSide,
      ).join() ||
    scorer !== (m.scorer?.id ?? '');

  const save = () => {
    setErr({});
    cmd.lineup.mutate(
      { id: f.id, matchId: m.id, sideA: a.filter(Boolean), sideB: b.filter(Boolean), scorerUserId: scorer || null, version: f.version },
      {
        onSuccess: () => toast.success(`${m.categoryCode} line-up saved`),
        onError: (e) => {
          if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setErr(e.fieldErrors);
          else toast.error(errorMessage(e));
        },
      },
    );
  };

  const picker = (side: 'A' | 'B', squad: PlayerRef[], value: string[], set: (v: string[]) => void, team: TeamRef | null) => (
    <Field label={team?.name ?? `Side ${side}`} error={err[`side${side}`]}>
      {({ id }) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {value.map((v, i) => {
            const options = eligible(squad, m.genderRule, i);
            const current = squad.find((p) => p.entryId === v);
            return (
              <Select
                key={i}
                id={i === 0 ? id : undefined}
                aria-label={`${team?.name ?? side} player ${i + 1}`}
                value={v}
                disabled={!canEdit}
                onChange={(e) => set(value.map((x, k) => (k === i ? e.target.value : x)))}
              >
                <option value="">Not picked</option>
                {current && !options.includes(current) && <option value={current.entryId}>{current.fullName}</option>}
                {options.map((p) => (
                  <option key={p.entryId} value={p.entryId}>
                    {p.fullName}
                  </option>
                ))}
              </Select>
            );
          })}
        </div>
      )}
    </Field>
  );

  return (
    <div className={s.matchCard}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <span className="chip c-neutral mono">{m.categoryCode}</span>
        <strong>
          {m.order}. {m.categoryName}
        </strong>
        <span className="t-caption">{GENDER_LABEL[m.genderRule]}</span>
        <span className="t-caption" style={{ marginLeft: 'auto' }}>
          {m.scheduledAt ? formatDateTime(m.scheduledAt).split(' · ')[1] : 'Time follows the tie'}
        </span>
        {m.status !== 'SCHEDULED' ? (
          <StatusChip status={m.status}>{m.status === 'SUBMITTED' ? 'Awaiting confirmation' : undefined}</StatusChip>
        ) : m.lineupComplete ? (
          <StatusChip tone="ok">Line-up set</StatusChip>
        ) : (
          <span className="chip c-pend">Line-up incomplete</span>
        )}
      </div>
      {m.status !== 'SCHEDULED' && (
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <span className="num" style={{ fontSize: 20 }}>
            {m.gamesA}–{m.gamesB}
          </span>
          {m.gameScores.length > 0 && (
            <GameScoreTiles compact games={m.gameScores.map((g) => g.split('-').map(Number)).map(([a, b]) => ({ a: a ?? 0, b: b ?? 0 }))} />
          )}
          {m.outcome && m.outcome !== 'NORMAL' && <span className="t-caption">{m.outcome === 'WALKOVER' ? 'Walkover' : 'Retired'}</span>}
        </div>
      )}
      <MatchRulesEditor m={m} canEdit={canSetRules && m.status === 'SCHEDULED'} />
      <div className={s.lineup}>
        {picker('A', squadA, a, setA, f.teamA)}
        {picker('B', squadB, b, setB, f.teamB)}
      </div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <Field label="Scorer" optional error={err.scorerUserId}>
          {({ id }) => (
            <Select id={id} value={scorer} disabled={!canEdit} onChange={(e) => setScorer(e.target.value)} style={{ minWidth: 220 }}>
              <option value="">Not assigned</option>
              {scorers.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.displayName}
                </option>
              ))}
            </Select>
          )}
        </Field>
        {canEdit && (
          <Button size="sm" variant="volt" disabled={!dirty} loading={cmd.lineup.isPending} onClick={save}>
            Save {m.categoryCode}
          </Button>
        )}
      </div>
    </div>
  );
}

function MatchRulesEditor({ m, canEdit }: { m: MatchView; canEdit: boolean }) {
  const defaults = useScoringSettings();
  const save = useMatchRules();
  const toast = useToast();
  const base = m.rulesCustom && m.pointsToWin !== null ? m : defaults.data;
  const [open, setOpen] = useState(false);
  const [v, setV] = useState({ pointsToWin: '', winBy: '', pointCap: '', gamesToWin: '2' });
  if (!base) return null;
  const current = {
    pointsToWin: base.pointsToWin ?? 21,
    winBy: base.winBy ?? 2,
    pointCap: base.pointCap ?? 30,
    gamesToWin: base.gamesToWin ?? 2,
  };
  const edit = () => {
    setV({
      pointsToWin: String(current.pointsToWin),
      winBy: String(current.winBy),
      pointCap: String(current.pointCap),
      gamesToWin: String(current.gamesToWin),
    });
    setOpen(true);
  };
  const done = (msg: string) => ({
    onSuccess: () => {
      setOpen(false);
      toast.success(msg);
    },
    onError: (e: unknown) => toast.error(errorMessage(e)),
  });
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
        <span className="t-caption">
          Format: {rulesSummary(current)}
          {m.rulesCustom ? ' · set for this match' : m.status === 'SCHEDULED' ? ' · tournament default' : ''}
        </span>
        {canEdit && !open && (
          <Button size="sm" variant="ghost" onClick={edit}>
            Change format
          </Button>
        )}
        {canEdit && !open && m.rulesCustom && (
          <Button
            size="sm"
            variant="ghost"
            loading={save.isPending}
            onClick={() => save.mutate({ matchId: m.id, reset: true }, done('Format reset to the default'))}
          >
            Use default
          </Button>
        )}
      </div>
      {open && (
        <div style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <Field label="Points">
            {({ id }) => (
              <Input
                id={id}
                inputMode="numeric"
                style={{ width: 80 }}
                value={v.pointsToWin}
                onChange={(e) => setV({ ...v, pointsToWin: e.target.value })}
              />
            )}
          </Field>
          <Field label="Win by">
            {({ id }) => (
              <Input
                id={id}
                inputMode="numeric"
                style={{ width: 80 }}
                value={v.winBy}
                onChange={(e) => setV({ ...v, winBy: e.target.value })}
              />
            )}
          </Field>
          <Field label="Cap">
            {({ id }) => (
              <Input
                id={id}
                inputMode="numeric"
                style={{ width: 80 }}
                value={v.pointCap}
                onChange={(e) => setV({ ...v, pointCap: e.target.value })}
              />
            )}
          </Field>
          <Field label="Games">
            {({ id }) => (
              <Select id={id} value={v.gamesToWin} onChange={(e) => setV({ ...v, gamesToWin: e.target.value })}>
                <option value="1">Best of 1</option>
                <option value="2">Best of 3</option>
                <option value="3">Best of 5</option>
              </Select>
            )}
          </Field>
          <Button
            size="sm"
            variant="volt"
            loading={save.isPending}
            onClick={() =>
              save.mutate(
                {
                  matchId: m.id,
                  pointsToWin: Number(v.pointsToWin),
                  winBy: Number(v.winBy),
                  pointCap: Number(v.pointCap),
                  gamesToWin: Number(v.gamesToWin),
                },
                done(`${m.categoryCode} format saved`),
              )
            }
          >
            Save format
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </div>
      )}
    </div>
  );
}

function pad(ids: string[], n: number) {
  return Array.from({ length: n }, (_, i) => ids[i] ?? '');
}

function PublishDialog({ onClose }: { onClose: () => void }) {
  const preview = usePublishPreview(true);
  const publish = usePublishFixtures();
  const toast = useToast();
  const items = preview.data?.items ?? [];
  return (
    <Modal
      open
      onClose={onClose}
      width={620}
      title="Publish fixtures"
      footer={
        <>
          <Button variant="out" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="volt"
            disabled={items.length === 0}
            loading={publish.isPending}
            onClick={() =>
              publish.mutate(undefined, {
                onSuccess: (r) => {
                  toast.success(`${r.published} ties published`);
                  onClose();
                },
                onError: (e) => toast.error(errorMessage(e)),
              })
            }
          >
            Publish {items.length}
          </Button>
        </>
      }
    >
      {preview.isLoading ? (
        <Skeleton height={160} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <p className="muted" style={{ margin: 0 }}>
            These ties will appear on the public Fixtures page exactly as they are now. Later edits stay private until you publish again.
          </p>
          {preview.data && preview.data.unscheduled > 0 && (
            <p className="help" style={{ margin: 0 }}>
              {preview.data.unscheduled} of them have no date yet — they show as “Date TBC”.
            </p>
          )}
          <ul
            style={{
              margin: 0,
              padding: 0,
              listStyle: 'none',
              maxHeight: 340,
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: 4,
            }}
          >
            {items.map((i) => (
              <li
                key={i.fixtureId}
                style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '6px 0', borderBottom: '1px solid var(--line)' }}
              >
                <span className="mono muted">#{i.number}</span>
                <span style={{ flex: 1 }}>{i.label}</span>
                {i.change === 'NEW' ? <StatusChip tone="live">New</StatusChip> : <StatusChip tone="pending">Changed</StatusChip>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </Modal>
  );
}
