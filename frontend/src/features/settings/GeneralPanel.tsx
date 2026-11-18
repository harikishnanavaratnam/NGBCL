'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, PhoneInput, Select, Textarea, Toggle } from '@/design-system/primitives/Inputs';
import { ConfirmDialog } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { useChangeStatus, useSaveGeneral, useSettings, type Settings, type TournamentPhase, type TournamentStatus } from '@/lib/api/settings';
import { useAuth } from '@/lib/auth/session';
import { formatRs } from '@/lib/format/money';
import { PanelError, PanelLoading } from './PanelStates';
import { SaveBar } from './SaveBar';
import styles from './Settings.module.css';
import { useSaveErrors } from './useSaveErrors';

const money = z
  .string()
  .trim()
  .refine((v) => v === '' || /^\d{1,12}$/.test(v.replace(/,/g, '')), 'Enter a whole amount in LKR');

const schema = z
  .object({
    name: z.string().trim().min(1, 'Enter the tournament name').max(160),
    shortName: z.string().trim().min(1, 'Enter a short name').max(40),
    tagline: z.string().max(160),
    description: z.string().max(4000),
    organiser: z.string().max(160),
    presenterLine: z.string().max(160),
    startsOn: z.string(),
    endsOn: z.string(),
    venue: z.string().max(200),
    city: z.string().max(80),
    courts: z.string().refine((v) => v === '' || (/^\d+$/.test(v) && +v >= 1 && +v <= 20), 'Between 1 and 20'),
    facebookUrl: z.string().refine((v) => v === '' || /^https:\/\//.test(v), 'Use a full https:// link'),
    contactEmail: z.string().refine((v) => v === '' || z.string().email().safeParse(v).success, 'Enter a valid email'),
    contactPhone: z.string().refine((v) => v === '' || /^[0-9 ()-]{7,20}$/.test(v), 'Enter a valid phone number'),
    prizeFirst: money,
    prizeSecond: money,
    prizeThird: money,
    teamEntryFee: money,
    showPlayerPrices: z.boolean(),
    showTeamBudgets: z.boolean(),
    phaseOverride: z.string(),
  })
  .refine((v) => !v.startsOn || !v.endsOn || v.endsOn >= v.startsOn, { path: ['endsOn'], message: 'Must be on or after the start date' });

type Values = z.infer<typeof schema>;

const PHASES: { value: TournamentPhase | ''; label: string }[] = [
  { value: '', label: 'Automatic (from status and registration dates)' },
  { value: 'PRE_LAUNCH', label: 'Pre-launch' },
  { value: 'REGISTRATION', label: 'Registration open' },
  { value: 'REGISTRATION_CLOSED', label: 'Registration closed' },
  { value: 'AUCTION', label: 'Auction day' },
  { value: 'RUNNING', label: 'Tournament running' },
  { value: 'COMPLETED', label: 'Completed' },
];

const NEXT_STATUS: Record<TournamentStatus, TournamentStatus[]> = {
  DRAFT: ['UPCOMING'],
  UPCOMING: ['DRAFT', 'ACTIVE'],
  ACTIVE: ['COMPLETED'],
  COMPLETED: ['ACTIVE', 'ARCHIVED'],
  ARCHIVED: [],
};

const STATUS_EFFECT: Record<TournamentStatus, string> = {
  DRAFT: 'Registrations stay closed to the public, even inside the registration window.',
  UPCOMING: 'Registrations open and close automatically on the dates in Registration settings.',
  ACTIVE: 'The public site switches to “Tournament running”.',
  COMPLETED: 'The public site shows the final results and champion banner.',
  ARCHIVED: 'The edition becomes read-only. This can’t be undone.',
};

function toValues(s: Settings): Values {
  const g = s.general;
  const p = s.publicInfo;
  const str = (v: number | null) => (v === null || v === undefined ? '' : String(v));
  return {
    name: g.name,
    shortName: g.shortName,
    tagline: g.tagline ?? '',
    description: g.description ?? '',
    organiser: g.organiser ?? '',
    presenterLine: g.presenterLine ?? '',
    startsOn: g.startsOn ?? '',
    endsOn: g.endsOn ?? '',
    venue: g.venue ?? '',
    city: g.city ?? '',
    courts: str(g.courts),
    facebookUrl: p.facebookUrl ?? '',
    contactEmail: p.contactEmail ?? '',
    contactPhone: (p.contactPhone ?? '').replace(/^\+94\s?/, ''),
    prizeFirst: str(p.prizeFirst),
    prizeSecond: str(p.prizeSecond),
    prizeThird: str(p.prizeThird),
    teamEntryFee: str(p.teamEntryFee),
    showPlayerPrices: p.showPlayerPrices,
    showTeamBudgets: p.showTeamBudgets,
    phaseOverride: p.phaseOverride ?? '',
  };
}

const num = (v: string) => (v.trim() === '' ? null : Number(v.replace(/,/g, '')));
const opt = (v: string) => (v.trim() === '' ? null : v.trim());

export function GeneralPanel() {
  const { data, isLoading, error, refetch } = useSettings();
  if (isLoading) return <PanelLoading />;
  if (error || !data) return <PanelError error={error} onRetry={() => void refetch()} />;
  return <GeneralForm settings={data} />;
}

function GeneralForm({ settings }: { settings: Settings }) {
  const toast = useToast();
  const { can } = useAuth();
  const canEdit = can('TOURNAMENT_MANAGE');
  const save = useSaveGeneral();
  const changeStatus = useChangeStatus();
  const [pendingStatus, setPendingStatus] = useState<TournamentStatus | null>(null);
  const defaults = useMemo(() => toValues(settings), [settings]);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    control,
    formState: { errors, isDirty },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: defaults });
  const onError = useSaveErrors(setError, {
    'publicInfo.facebookUrl': 'facebookUrl',
    'publicInfo.contactEmail': 'contactEmail',
    'publicInfo.contactPhone': 'contactPhone',
  });

  useEffect(() => reset(defaults), [defaults, reset]);

  const onSubmit = handleSubmit((v) =>
    save.mutate(
      {
        name: v.name,
        shortName: v.shortName,
        tagline: opt(v.tagline),
        description: opt(v.description),
        organiser: opt(v.organiser),
        presenterLine: opt(v.presenterLine),
        startsOn: opt(v.startsOn),
        endsOn: opt(v.endsOn),
        venue: opt(v.venue),
        city: opt(v.city),
        courts: num(v.courts),
        tournamentVersion: settings.general.tournamentVersion,
        settingsVersion: settings.settingsVersion,
        publicInfo: {
          facebookUrl: opt(v.facebookUrl),
          contactEmail: opt(v.contactEmail),
          contactPhone: v.contactPhone.trim() ? `+94 ${v.contactPhone.trim()}` : null,
          prizeFirst: num(v.prizeFirst),
          prizeSecond: num(v.prizeSecond),
          prizeThird: num(v.prizeThird),
          teamEntryFee: num(v.teamEntryFee),
          showPlayerPrices: v.showPlayerPrices,
          showTeamBudgets: v.showTeamBudgets,
          phaseOverride: (v.phaseOverride || null) as TournamentPhase | null,
        },
      },
      { onSuccess: () => toast.success('General settings saved'), onError },
    ),
  );

  const prizeValues = useWatch({ control, name: ['prizeFirst', 'prizeSecond', 'prizeThird'] });
  const prizes = prizeValues.map(num);
  const pool = prizes.every((p) => p === null) ? null : prizes.reduce<number>((s, p) => s + (p ?? 0), 0);
  const status = settings.general.status;

  return (
    <form className={styles.panel} onSubmit={onSubmit} noValidate>
      <fieldset disabled={!canEdit} style={{ border: 0, padding: 0, margin: 0, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 24 }}>
        <section className={styles.section} aria-labelledby="gen-title">
          <h2 id="gen-title" className="t-h2">
            General
          </h2>
          <div className={styles.grid2}>
            <Field label="Tournament name" required error={errors.name?.message}>
              {({ id, describedBy }) => <Input id={id} aria-describedby={describedBy} {...register('name')} />}
            </Field>
            <Field label="Short name" required error={errors.shortName?.message}>
              {({ id }) => <Input id={id} {...register('shortName')} />}
            </Field>
            <Field label="Edition" help="Part of the name — the next season becomes 2.0.">
              {({ id }) => <Input id={id} value={settings.general.edition} disabled readOnly />}
            </Field>
            <Field label="Tagline" optional error={errors.tagline?.message}>
              {({ id }) => <Input id={id} placeholder="Rise. Rally. Rule the Court." {...register('tagline')} />}
            </Field>
          </div>
          <Field label="Public description" optional help="Shown in the About section of the home page.">
            {({ id }) => <Textarea id={id} rows={3} {...register('description')} />}
          </Field>
          <div className={styles.grid2}>
            <Field label="Organiser" optional>
              {({ id }) => <Input id={id} {...register('organiser')} />}
            </Field>
            <Field label="Presenter line" optional help="Shown above the logo on the hero and auction display.">
              {({ id }) => <Input id={id} placeholder="Mighty Sharks Battalion presents" {...register('presenterLine')} />}
            </Field>
          </div>
        </section>

        <hr className="court" />

        <section className={styles.section} aria-labelledby="when-title">
          <h2 id="when-title" className="t-h3">
            When and where
          </h2>
          <div className={styles.grid3}>
            <Field label="Start date" optional help="Empty shows as “TBC” on the public site.">
              {({ id }) => <Input id={id} type="date" {...register('startsOn')} />}
            </Field>
            <Field label="End date" optional error={errors.endsOn?.message}>
              {({ id, describedBy }) => <Input id={id} type="date" aria-describedby={describedBy} {...register('endsOn')} />}
            </Field>
            <Field label="Time zone" help="Stored in UTC, shown in this zone.">
              {({ id }) => <Input id={id} value="Asia/Colombo (UTC+05:30)" disabled readOnly />}
            </Field>
            <Field label="Venue" optional>
              {({ id }) => <Input id={id} placeholder="Venue name" {...register('venue')} />}
            </Field>
            <Field label="City" optional>
              {({ id }) => <Input id={id} {...register('city')} />}
            </Field>
            <Field label="Courts" optional help="Used by the schedule board." error={errors.courts?.message}>
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} {...register('courts')} />}
            </Field>
          </div>
        </section>

        <hr className="court" />

        <section className={styles.section} aria-labelledby="status-title">
          <h2 id="status-title" className="t-h3">
            Status
          </h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <StatusChip status={status} />
            <span className="t-body-s muted">{STATUS_EFFECT[status]}</span>
          </div>
          {NEXT_STATUS[status].length > 0 && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {NEXT_STATUS[status].map((s) => (
                <Button key={s} size="sm" variant={s === 'ARCHIVED' ? 'danger' : 'out'} onClick={() => setPendingStatus(s)}>
                  Move to {s.toLowerCase()}
                </Button>
              ))}
            </div>
          )}
          <p className="t-caption">
            Public phase right now: <strong>{PHASES.find((p) => p.value === settings.general.phase)?.label ?? settings.general.phase}</strong>
          </p>
        </section>

        <hr className="court" />

        <section className={styles.section} aria-labelledby="public-title">
          <h2 id="public-title" className="t-h3">
            Public information
          </h2>
          <div className={styles.grid3}>
            <Field label="Champions prize (LKR)" optional error={errors.prizeFirst?.message}>
              {({ id }) => <Input id={id} inputMode="numeric" {...register('prizeFirst')} />}
            </Field>
            <Field label="Runners-up prize (LKR)" optional error={errors.prizeSecond?.message}>
              {({ id }) => <Input id={id} inputMode="numeric" {...register('prizeSecond')} />}
            </Field>
            <Field label="Third place prize (LKR)" optional error={errors.prizeThird?.message}>
              {({ id }) => <Input id={id} inputMode="numeric" {...register('prizeThird')} />}
            </Field>
          </div>
          {pool !== null && <span className="t-caption">Prize pool shown publicly: {formatRs(pool)}</span>}
          <div className={styles.grid3}>
            <Field label="Team entry fee (LKR)" optional help="Information only — no online payment." error={errors.teamEntryFee?.message}>
              {({ id }) => <Input id={id} inputMode="numeric" {...register('teamEntryFee')} />}
            </Field>
            <Field label="Organiser contact email" optional help="Public — shown in the footer." error={errors.contactEmail?.message}>
              {({ id }) => <Input id={id} type="email" {...register('contactEmail')} />}
            </Field>
            <Field label="Organiser contact number" optional help="Public — shown in the footer." error={errors.contactPhone?.message}>
              {({ id }) => <PhoneInput id={id} {...register('contactPhone')} />}
            </Field>
          </div>
          <Field label="Facebook page" optional error={errors.facebookUrl?.message}>
            {({ id }) => <Input id={id} type="url" placeholder="https://www.facebook.com/…" {...register('facebookUrl')} />}
          </Field>
          <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
            <Toggle id="spp" label="Show player auction prices publicly" {...register('showPlayerPrices')} />
            <Toggle id="stb" label="Show team budgets publicly" {...register('showTeamBudgets')} />
          </div>
          <Field label="Home page phase" help="Leave on Automatic. Override only for special moments, e.g. auction day before the auction console is live.">
            {({ id }) => (
              <Select id={id} {...register('phaseOverride')}>
                {PHASES.map((p) => (
                  <option key={p.value} value={p.value}>
                    {p.label}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        </section>
      </fieldset>

      {!canEdit && <p className="t-caption">You can view these settings; only a Tournament Admin can change them.</p>}
      <SaveBar dirty={isDirty} saving={save.isPending} updatedAt={settings.updatedAt} onDiscard={() => reset(defaults)} />

      <ConfirmDialog
        open={pendingStatus !== null}
        onClose={() => setPendingStatus(null)}
        tone={pendingStatus === 'ARCHIVED' ? 'danger' : 'volt'}
        typeToConfirm={pendingStatus === 'ARCHIVED' ? 'ARCHIVE' : undefined}
        title={`Move the tournament to ${pendingStatus?.toLowerCase()}?`}
        confirmLabel={`Move to ${pendingStatus?.toLowerCase()}`}
        consequences={<p>{pendingStatus ? STATUS_EFFECT[pendingStatus] : ''} The change is recorded in the audit log.</p>}
        onConfirm={() =>
          new Promise<void>((resolve) =>
            changeStatus.mutate(
              { status: pendingStatus! },
              {
                onSuccess: () => {
                  toast.success(`Tournament is now ${pendingStatus!.toLowerCase()}`);
                  resolve();
                },
                onError: (e) => {
                  onError(e);
                  resolve();
                },
              },
            ),
          )
        }
      />
    </form>
  );
}
