'use client';

import { useState } from 'react';
import { ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Toggle } from '@/design-system/primitives/Inputs';
import { ConfirmDialog } from '@/design-system/primitives/Overlay';
import { RadioCards } from '@/design-system/primitives/RadioCard';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { CrestTile } from '@/design-system/sport/CrestTile';
import {
  FORMAT_LABEL,
  useCompetitionSetup,
  useDeleteFixtures,
  useDrawGroups,
  useFixtures,
  useGenerateFixtures,
  useGroups,
  useSetupCommands,
  type CompetitionFormat,
  type CompetitionSettings,
  type TeamRef,
} from '@/lib/api/competition';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { useAuth } from '@/lib/auth/session';
import s from './Competition.module.css';

const FORMAT_HELP: Record<CompetitionFormat, string> = {
  GROUPS_KNOCKOUT: 'Groups play round robin; the top teams go into semi-finals (or quarter-finals) and a final.',
  ROUND_ROBIN: 'Everyone plays everyone once. The league table decides the champion.',
  ROUND_ROBIN_FINAL: 'Everyone plays everyone once, then the top two play a final.',
  KNOCKOUT: 'Single elimination from the first tie. Top seeds get byes if the numbers are uneven.',
};

/** Admin › Competition › Format: rules, courts, group draw and fixture generation (FIX-001/002). */
export function FormatPage() {
  const setup = useCompetitionSetup();
  const groups = useGroups();
  const fixtures = useFixtures();
  if (setup.isLoading || groups.isLoading || fixtures.isLoading) {
    return (
      <div className={s.page} aria-busy="true">
        <Skeleton height={40} width={300} />
        <Skeleton height={320} />
        <Skeleton height={240} />
      </div>
    );
  }
  if (setup.error || groups.error || fixtures.error || !setup.data || !groups.data || !fixtures.data) {
    return <ErrorState title="Couldn’t load the competition" message={errorMessage(setup.error ?? groups.error ?? fixtures.error)} onRetry={() => void Promise.all([setup.refetch(), groups.refetch(), fixtures.refetch()])} />;
  }
  return <FormatBody key={String(setup.data.settings.version)} />;
}

function FormatBody() {
  const { data: setup } = useCompetitionSetup();
  const { data: groups } = useGroups();
  const { data: fx } = useFixtures();
  const { can } = useAuth();
  const canEdit = can('FIXTURE_MANAGE');
  const toast = useToast();
  const cmd = useSetupCommands();
  const draw = useDrawGroups();
  const generate = useGenerateFixtures();
  const remove = useDeleteFixtures();
  const [form, setForm] = useState<CompetitionSettings>(setup!.settings);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [newCourt, setNewCourt] = useState('');
  const [seeds, setSeeds] = useState<TeamRef[] | null>(null);
  const [confirm, setConfirm] = useState<'generate' | 'delete' | null>(null);

  const settings = setup!.settings;
  const g = groups!;
  const view = fx!;
  const dirty = JSON.stringify({ ...form, version: null }) !== JSON.stringify({ ...settings, version: null });
  const allTeams = [...g.groups.flatMap((x) => x.teams), ...g.unassigned];
  const seedList = seeds ?? allTeams;
  const generated = view.fixtures.length > 0;
  const published = view.fixtures.some((f) => f.published);
  const usesGroups = settings.format === 'GROUPS_KNOCKOUT';
  const usesSeeds = settings.format === 'KNOCKOUT';

  const set = <K extends keyof CompetitionSettings>(k: K, v: CompetitionSettings[K]) => setForm((f) => ({ ...f, [k]: v }));
  const num = (k: keyof CompetitionSettings) => (e: React.ChangeEvent<HTMLInputElement>) => set(k, Number(e.target.value.replace(/\D/g, '')) as never);

  const save = () => {
    setErrors({});
    cmd.settings.mutate(form, {
      onSuccess: () => toast.success('Competition rules saved'),
      onError: (e) => {
        if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setErrors(e.fieldErrors);
        else toast.error(errorMessage(e));
      },
    });
  };

  const moveSeed = (i: number, dir: -1 | 1) => {
    const next = [...seedList];
    const j = i + dir;
    if (j < 0 || j >= next.length) return;
    [next[i], next[j]] = [next[j], next[i]];
    setSeeds(next);
  };

  const shuffle = () => {
    const next = [...seedList];
    for (let i = next.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [next[i], next[j]] = [next[j], next[i]];
    }
    setSeeds(next);
  };

  const teamsPerGroup = settings.groupCount > 0 ? Math.ceil(allTeams.length / settings.groupCount) : 0;
  const summary =
    settings.format === 'GROUPS_KNOCKOUT'
      ? `${settings.groupCount} groups of about ${teamsPerGroup} teams · top ${settings.qualifiersPerGroup} from each go through (${settings.groupCount * settings.qualifiersPerGroup} teams)${settings.thirdPlaceMatch ? ' · 3rd place match' : ''}`
      : settings.format === 'KNOCKOUT'
        ? `${allTeams.length} teams in a single-elimination bracket${settings.thirdPlaceMatch ? ' · 3rd place match' : ''}`
        : `${allTeams.length} teams, ${(allTeams.length * (allTeams.length - 1)) / 2} ties${settings.format === 'ROUND_ROBIN_FINAL' ? ' · then a final' : ''}`;

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <nav aria-label="Breadcrumb" className="t-caption">
            Competition / Format
          </nav>
          <h1 className="t-h1">Format and draw</h1>
          <p className="muted t-body-s" style={{ margin: 0 }}>
            {FORMAT_LABEL[settings.format]} · {summary}. Each tie has {setup!.template.length} matches ({setup!.template.map((t) => t.code).join(', ')}).
          </p>
        </div>
        <Button href="/admin/competition/fixtures" variant="out">
          Fixtures
        </Button>
      </div>

      <section className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 18 }} aria-labelledby="fmt-h">
        <h2 id="fmt-h" className="t-h2">
          Rules
        </h2>
        <fieldset disabled={!canEdit} style={{ border: 0, padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 18 }}>
          <RadioCards
            name="format"
            legend="Tournament format"
            value={form.format}
            onChange={(v) => set('format', v)}
            columns={2}
            options={(Object.keys(FORMAT_LABEL) as CompetitionFormat[]).map((f) => ({ value: f, label: FORMAT_LABEL[f], description: FORMAT_HELP[f] }))}
          />
          <div className={s.grid3}>
            {form.format === 'GROUPS_KNOCKOUT' && (
              <>
                <Field label="Number of groups" error={errors.groupCount}>
                  {({ id }) => <Input id={id} inputMode="numeric" value={form.groupCount} onChange={num('groupCount')} />}
                </Field>
                <Field label="Teams that qualify from each group" error={errors.qualifiersPerGroup} help="Groups × qualifiers must be 2, 4, 8 or 16.">
                  {({ id }) => <Input id={id} inputMode="numeric" value={form.qualifiersPerGroup} onChange={num('qualifiersPerGroup')} />}
                </Field>
              </>
            )}
            <Field label="Matches a player may play per tie" error={errors.maxMatchesPerPlayer}>
              {({ id }) => <Input id={id} inputMode="numeric" value={form.maxMatchesPerPlayer} onChange={num('maxMatchesPerPlayer')} />}
            </Field>
            <Field label="Minutes per match" error={errors.matchMinutes} help="Used to place matches back to back and to spot clashes.">
              {({ id }) => <Input id={id} inputMode="numeric" value={form.matchMinutes} onChange={num('matchMinutes')} />}
            </Field>
            <Field label="Break between matches (minutes)" error={errors.restMinutes}>
              {({ id }) => <Input id={id} inputMode="numeric" value={form.restMinutes} onChange={num('restMinutes')} />}
            </Field>
          </div>
          {form.format !== 'ROUND_ROBIN' && <Toggle id="third" label="Play a 3rd place match" checked={form.thirdPlaceMatch} onChange={(e) => set('thirdPlaceMatch', e.target.checked)} />}
          {canEdit && (
            <div style={{ display: 'flex', gap: 8 }}>
              <Button variant="volt" disabled={!dirty} loading={cmd.settings.isPending} onClick={save}>
                Save rules
              </Button>
              {dirty && (
                <Button variant="ghost" onClick={() => setForm(settings)}>
                  Discard
                </Button>
              )}
            </div>
          )}
          {generated && dirty && <span className="help">Fixtures already exist. Format changes apply when you regenerate them.</span>}
        </fieldset>
      </section>

      <section className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 14 }} aria-labelledby="courts-h">
        <h2 id="courts-h" className="t-h3">
          Courts
        </h2>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
          {setup!.courts.map((c) => (
            <CourtChip key={c.id} id={c.id} name={c.name} active={c.active} version={c.version} canEdit={canEdit} />
          ))}
        </div>
        {canEdit && (
          <div style={{ display: 'flex', gap: 8, maxWidth: 420 }}>
            <Input aria-label="New court name" placeholder="e.g. Court 4" value={newCourt} onChange={(e) => setNewCourt(e.target.value)} />
            <Button
              variant="out"
              disabled={!newCourt.trim()}
              loading={cmd.court.isPending}
              onClick={() =>
                cmd.court.mutate(
                  { name: newCourt.trim() },
                  {
                    onSuccess: () => {
                      setNewCourt('');
                      toast.success('Court added');
                    },
                    onError: (e) => toast.error(errorMessage(e)),
                  },
                )
              }
            >
              Add court
            </Button>
          </div>
        )}
      </section>

      {(usesGroups || usesSeeds) && (
        <section className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 14 }} aria-labelledby="draw-h">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <h2 id="draw-h" className="t-h3">
              {usesGroups ? 'Seeding and group draw' : 'Seeding'}
            </h2>
            {g.locked && <StatusChip tone="pending">Locked — fixtures exist</StatusChip>}
          </div>
          <p className="help" style={{ margin: 0 }}>
            {usesGroups
              ? 'Order the teams by seed (or shuffle for a random draw). The snake draw spreads seeds across groups: 1 → A, 2 → B … then back.'
              : 'Order the teams by seed. Seeds 1 and 2 can only meet in the final; top seeds get any byes.'}
          </p>
          <div className={s.drawGrid}>
            <ol className={s.seedList}>
              {seedList.map((t, i) => (
                <li key={t.id}>
                  <span className="mono muted" style={{ width: 22 }}>
                    {i + 1}
                  </span>
                  <CrestTile logoUrl={t.logoUrl} name={t.name} colorSlot={t.colorSlot} size="s" />
                  <span style={{ flex: 1 }}>{t.name}</span>
                  {canEdit && !g.locked && (
                    <>
                      <Button size="sm" variant="ghost" aria-label={`Move ${t.name} up`} disabled={i === 0} onClick={() => moveSeed(i, -1)}>
                        ↑
                      </Button>
                      <Button size="sm" variant="ghost" aria-label={`Move ${t.name} down`} disabled={i === seedList.length - 1} onClick={() => moveSeed(i, 1)}>
                        ↓
                      </Button>
                    </>
                  )}
                </li>
              ))}
            </ol>
            {usesGroups && (
              <div className={s.groupCards}>
                {g.groups.length === 0 && <p className="muted">No draw yet.</p>}
                {g.groups.map((grp) => (
                  <div key={grp.id} className="panel-flat" style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <strong>{grp.name}</strong>
                    {grp.teams.map((t) => (
                      <span key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <CrestTile logoUrl={t.logoUrl} name={t.name} colorSlot={t.colorSlot} size="s" />
                        {t.name}
                      </span>
                    ))}
                  </div>
                ))}
                {g.unassigned.length > 0 && g.groups.length > 0 && <p className="help">{g.unassigned.length} active teams are not in a group — redo the draw.</p>}
              </div>
            )}
          </div>
          {canEdit && !g.locked && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Button variant="out" onClick={shuffle}>
                Shuffle
              </Button>
              {usesGroups && (
                <Button
                  variant="volt"
                  loading={draw.isPending}
                  disabled={seedList.length < 2}
                  onClick={() =>
                    draw.mutate(
                      seedList.map((t) => t.id),
                      {
                        onSuccess: () => {
                          setSeeds(null);
                          toast.success('Groups drawn');
                        },
                        onError: (e) => toast.error(errorMessage(e)),
                      },
                    )
                  }
                >
                  Draw {settings.groupCount} groups
                </Button>
              )}
            </div>
          )}
        </section>
      )}

      <section className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 12 }} aria-labelledby="gen-h">
        <h2 id="gen-h" className="t-h3">
          Fixtures
        </h2>
        <p className="muted" style={{ margin: 0 }}>
          {generated
            ? `${view.fixtures.length} ties exist (${view.fixtures.filter((f) => f.published).length} published).`
            : 'Generate every tie for the format above. Each tie gets the template’s matches; you schedule them and pick line-ups afterwards.'}
        </p>
        {published ? (
          <p className="help" style={{ margin: 0 }}>
            Fixtures are published, so they can’t be regenerated. Edit ties one by one on the Fixtures page.
          </p>
        ) : (
          canEdit && (
            <div style={{ display: 'flex', gap: 8 }}>
              <Button variant="volt" loading={generate.isPending} onClick={() => setConfirm('generate')}>
                {generated ? 'Regenerate fixtures' : 'Generate fixtures'}
              </Button>
              {generated && (
                <Button variant="danger" onClick={() => setConfirm('delete')}>
                  Delete all fixtures
                </Button>
              )}
            </div>
          )
        )}
      </section>

      <ConfirmDialog
        open={confirm === 'generate'}
        onClose={() => setConfirm(null)}
        title={generated ? 'Regenerate all fixtures?' : 'Generate fixtures?'}
        confirmLabel={generated ? 'Regenerate' : 'Generate'}
        consequences={
          <p style={{ margin: 0 }}>
            {FORMAT_LABEL[settings.format]}: {summary}. {generated ? 'Existing draft ties, schedules and line-ups are replaced.' : 'Nothing is public until you publish.'}
          </p>
        }
        onConfirm={() =>
          new Promise<void>((resolve) =>
            generate.mutate(usesSeeds ? seedList.map((t) => t.id) : undefined, {
              onSuccess: (v) => {
                toast.success(`${v.fixtures.length} ties created`);
                resolve();
              },
              onError: (e) => {
                toast.error(errorMessage(e));
                resolve();
              },
            }),
          )
        }
      />
      <ConfirmDialog
        open={confirm === 'delete'}
        onClose={() => setConfirm(null)}
        tone="danger"
        typeToConfirm="DELETE"
        title="Delete all fixtures?"
        confirmLabel="Delete fixtures"
        consequences={<p style={{ margin: 0 }}>All draft ties, schedules and line-ups are removed. The group draw stays and can then be changed.</p>}
        onConfirm={() =>
          new Promise<void>((resolve) =>
            remove.mutate(undefined, {
              onSuccess: () => {
                toast.success('Fixtures deleted');
                resolve();
              },
              onError: (e) => {
                toast.error(errorMessage(e));
                resolve();
              },
            }),
          )
        }
      />
    </div>
  );
}

function CourtChip({ id, name, active, version, canEdit }: { id: string; name: string; active: boolean; version: number; canEdit: boolean }) {
  const cmd = useSetupCommands();
  const toast = useToast();
  const [edit, setEdit] = useState<string | null>(null);
  if (edit !== null) {
    return (
      <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
        <Input aria-label="Court name" value={edit} onChange={(e) => setEdit(e.target.value)} style={{ width: 160, minHeight: 36 }} autoFocus />
        <Button
          size="sm"
          variant="volt"
          disabled={!edit.trim()}
          onClick={() => cmd.court.mutate({ id, name: edit.trim(), version }, { onSuccess: () => setEdit(null), onError: (e) => toast.error(errorMessage(e)) })}
        >
          Save
        </Button>
        <Button size="sm" variant="ghost" onClick={() => setEdit(null)}>
          Cancel
        </Button>
      </span>
    );
  }
  return (
    <span className="panel-flat" style={{ display: 'inline-flex', gap: 8, alignItems: 'center', padding: '6px 10px', opacity: active ? 1 : 0.6 }}>
      <strong style={{ fontSize: 14 }}>{name}</strong>
      {!active && <span className="chip c-neutral">Not in use</span>}
      {canEdit && (
        <>
          <Button size="sm" variant="ghost" onClick={() => setEdit(name)}>
            Rename
          </Button>
          <Button size="sm" variant="ghost" onClick={() => cmd.court.mutate({ id, name, active: !active, version }, { onError: (e) => toast.error(errorMessage(e)) })}>
            {active ? 'Stop using' : 'Use'}
          </Button>
        </>
      )}
    </span>
  );
}
