'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select, Toggle } from '@/design-system/primitives/Inputs';
import { useToast } from '@/design-system/primitives/Toast';
import { useSaveScoringSettings, useScoringSettings, type ScoringSettings } from '@/lib/api/scoring';
import { useAuth } from '@/lib/auth/session';
import { PanelError, PanelLoading } from '../settings/PanelStates';
import { SaveBar } from '../settings/SaveBar';
import styles from '../settings/Settings.module.css';
import { useSaveErrors } from '../settings/useSaveErrors';

const int = (min: number, max: number, label: string) =>
  z
    .string()
    .trim()
    .refine((v) => /^\d{1,2}$/.test(v), `Enter ${label} as a whole number`)
    .refine((v) => Number(v) >= min && Number(v) <= max, `Between ${min} and ${max}`);

const schema = z
  .object({
    pointsToWin: int(5, 50, 'the points'),
    winBy: int(1, 5, 'the margin'),
    pointCap: int(5, 99, 'the cap'),
    gamesToWin: z.enum(['1', '2', '3']),
    allowWalkover: z.boolean(),
    allowRetirement: z.boolean(),
    twoStepApproval: z.boolean(),
  })
  .refine((v) => Number(v.pointCap) >= Number(v.pointsToWin), { path: ['pointCap'], message: 'Must be at least the points to win' });

type Values = z.infer<typeof schema>;

const toValues = (s: ScoringSettings): Values => ({
  pointsToWin: String(s.pointsToWin),
  winBy: String(s.winBy),
  pointCap: String(s.pointCap),
  gamesToWin: String(s.gamesToWin) as Values['gamesToWin'],
  allowWalkover: s.allowWalkover,
  allowRetirement: s.allowRetirement,
  twoStepApproval: s.twoStepApproval,
});

export function rulesSummary(r: { pointsToWin: number; winBy: number; pointCap: number; gamesToWin: number }) {
  const deuce = r.winBy > 1 ? ` · win by ${r.winBy}` : '';
  const cap = r.pointCap > r.pointsToWin ? ` · cap ${r.pointCap}` : '';
  return `${r.pointsToWin} pts${deuce}${cap} · best of ${r.gamesToWin * 2 - 1}`;
}

/** Admin › Settings › Scoring rules (SCR-001..004): default match format and approval flow. */
export function ScoringSettingsPanel() {
  const { data, isLoading, error, refetch } = useScoringSettings();
  if (isLoading) return <PanelLoading />;
  if (error || !data) return <PanelError error={error} onRetry={() => void refetch()} />;
  return <ScoringSettingsForm settings={data} />;
}

function ScoringSettingsForm({ settings }: { settings: ScoringSettings }) {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const canEdit = can('SETTINGS_MANAGE') || can('FIXTURE_MANAGE');
  const save = useSaveScoringSettings();
  const defaults = useMemo(() => toValues(settings), [settings]);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    control,
    formState: { errors, isDirty },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: defaults });
  const showErrors = useSaveErrors(setError);

  useEffect(() => reset(defaults), [defaults, reset]);

  const onSubmit = handleSubmit((v) =>
    save.mutate(
      {
        pointsToWin: Number(v.pointsToWin),
        winBy: Number(v.winBy),
        pointCap: Number(v.pointCap),
        gamesToWin: Number(v.gamesToWin),
        allowWalkover: v.allowWalkover,
        allowRetirement: v.allowRetirement,
        twoStepApproval: v.twoStepApproval,
        version: settings.version ?? null,
      },
      {
        onSuccess: () => toast.success('Scoring rules saved'),
        onError: (e) => {
          void qc.invalidateQueries({ queryKey: ['scoring', 'settings'] });
          showErrors(e);
        },
      },
    ),
  );

  const [p, w, c, g] = useWatch({ control, name: ['pointsToWin', 'winBy', 'pointCap', 'gamesToWin'] });
  const preview = [p, w, c, g].every((x) => /^\d+$/.test(x ?? ''))
    ? rulesSummary({ pointsToWin: +p, winBy: +w, pointCap: +c, gamesToWin: +g })
    : null;

  return (
    <form className={styles.panel} onSubmit={onSubmit} noValidate>
      <fieldset
        disabled={!canEdit}
        style={{ border: 0, padding: 0, margin: 0, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 24 }}
      >
        <section className={styles.section} aria-labelledby="scr-title">
          <h2 id="scr-title" className="t-h2">
            Scoring rules
          </h2>
          <p className="t-body-s muted" style={{ margin: 0 }}>
            The default format for every match. Organisers can change the format of a single match on the fixtures page until it starts.
            Changes here apply to matches that haven’t started yet.
          </p>
          <div className={styles.grid2}>
            <Field label="Points to win a game" required error={errors.pointsToWin?.message}>
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} {...register('pointsToWin')} />}
            </Field>
            <Field label="Games" required error={errors.gamesToWin?.message}>
              {({ id }) => (
                <Select id={id} {...register('gamesToWin')}>
                  <option value="1">Best of 1</option>
                  <option value="2">Best of 3</option>
                  <option value="3">Best of 5</option>
                </Select>
              )}
            </Field>
            <Field label="Win by (deuce margin)" required error={errors.winBy?.message} help="1 means no deuce.">
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} {...register('winBy')} />}
            </Field>
            <Field label="Point cap" required error={errors.pointCap?.message} help="First to this score wins the game, even by one point.">
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} {...register('pointCap')} />}
            </Field>
          </div>
          {preview && (
            <p className="t-caption" style={{ margin: 0 }}>
              Scorers will see: <strong>{preview}</strong>
            </p>
          )}
        </section>
        <section className={styles.section} aria-labelledby="scr-res">
          <h2 id="scr-res" className="t-h2">
            Results
          </h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Toggle
              id="two"
              label="Two-step approval — a referee or tournament admin confirms each submitted result"
              {...register('twoStepApproval')}
            />
            <Toggle id="wo" label="Allow walkovers" {...register('allowWalkover')} />
            <Toggle id="ret" label="Allow retirements" {...register('allowRetirement')} />
          </div>
          <p className="t-caption" style={{ margin: 0 }}>
            With two-step approval off, a scorer’s submitted result counts immediately. Live scores always show on the public site as
            provisional until the result is confirmed.
          </p>
        </section>
      </fieldset>
      {!canEdit && <p className="t-caption">You can view these rules; only users with settings or fixture permission can change them.</p>}
      <SaveBar dirty={isDirty} saving={save.isPending} onDiscard={() => reset(defaults)} />
    </form>
  );
}
