'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input } from '@/design-system/primitives/Inputs';
import { useToast } from '@/design-system/primitives/Toast';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { useSaveStandingsRules, useStandingsRules, type StandingsRules, type Tiebreaker } from '@/lib/api/standings';
import { useAuth } from '@/lib/auth/session';
import { PanelError, PanelLoading } from '../settings/PanelStates';
import { SaveBar } from '../settings/SaveBar';
import styles from '../settings/Settings.module.css';
import { TIEBREAKER_LABEL } from './StandingsTable';

const ALL: Tiebreaker[] = ['MATCHES_WON', 'MATCH_DIFF', 'GAME_DIFF', 'POINT_DIFF', 'GAMES_WON', 'HEAD_TO_HEAD'];

/** Settings › Standings (STA-001..004): table points and the tie-breaker order. */
export function StandingsSettingsPanel() {
  const { data, isLoading, error, refetch } = useStandingsRules();
  if (isLoading) return <PanelLoading />;
  if (error || !data) return <PanelError error={error} onRetry={() => void refetch()} />;
  return <StandingsForm key={data.version ?? 'new'} rules={data} />;
}

interface Draft {
  pointsWin: string;
  pointsDraw: string;
  pointsLoss: string;
  order: Tiebreaker[];
  on: Tiebreaker[];
}

const toDraft = (r: StandingsRules): Draft => ({
  pointsWin: String(r.pointsWin),
  pointsDraw: String(r.pointsDraw),
  pointsLoss: String(r.pointsLoss),
  order: [...r.tiebreakers, ...ALL.filter((x) => !r.tiebreakers.includes(x))],
  on: r.tiebreakers,
});

function StandingsForm({ rules }: { rules: StandingsRules }) {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const canEdit = can('STANDINGS_MANAGE', 'SETTINGS_MANAGE');
  const save = useSaveStandingsRules();
  const initial = useMemo(() => toDraft(rules), [rules]);
  const [d, setD] = useState<Draft>(initial);
  const [errs, setErrs] = useState<Record<string, string>>({});
  const active = d.order.filter((x) => d.on.includes(x));
  const dirty = JSON.stringify({ ...d, order: active }) !== JSON.stringify({ ...initial, order: initial.on });

  const move = (i: number, by: -1 | 1) => {
    const o = [...d.order];
    const j = i + by;
    if (j < 0 || j >= o.length) return;
    [o[i], o[j]] = [o[j]!, o[i]!];
    setD({ ...d, order: o });
  };

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const n = (v: string) => (/^\d{1,2}$/.test(v.trim()) ? Number(v) : NaN);
    const local: Record<string, string> = {};
    for (const k of ['pointsWin', 'pointsDraw', 'pointsLoss'] as const) {
      if (Number.isNaN(n(d[k])) || n(d[k]) > 10) local[k] = 'A whole number from 0 to 10';
    }
    if (
      !local.pointsDraw &&
      !local.pointsWin &&
      !local.pointsLoss &&
      (n(d.pointsDraw) > n(d.pointsWin) || n(d.pointsLoss) > n(d.pointsDraw))
    ) {
      local.pointsDraw = 'A win must be worth at least a draw, and a draw at least a loss';
    }
    setErrs(local);
    if (Object.keys(local).length) return;
    save.mutate(
      { pointsWin: n(d.pointsWin), pointsDraw: n(d.pointsDraw), pointsLoss: n(d.pointsLoss), tiebreakers: active, version: rules.version },
      {
        onSuccess: () => toast.success('Standings rules saved'),
        onError: (er) => {
          void qc.invalidateQueries({ queryKey: ['standings', 'settings'] });
          if (er instanceof ApiError && Object.keys(er.fieldErrors).length) setErrs(er.fieldErrors);
          else toast.error(errorMessage(er));
        },
      },
    );
  };

  return (
    <form className={styles.panel} onSubmit={submit} noValidate>
      <fieldset
        disabled={!canEdit}
        style={{ border: 0, padding: 0, margin: 0, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 24 }}
      >
        <section className={styles.section} aria-labelledby="st-title">
          <h2 id="st-title" className="t-h2">
            Standings
          </h2>
          <p className="t-body-s muted" style={{ margin: 0 }}>
            Group tables rank teams by table points from tie results, then by the tie-breakers below in order. Only confirmed results count.
          </p>
          <div className={styles.grid3}>
            <Field label="Points for a tie won" required error={errs.pointsWin}>
              {({ id }) => (
                <Input id={id} inputMode="numeric" value={d.pointsWin} onChange={(e) => setD({ ...d, pointsWin: e.target.value })} />
              )}
            </Field>
            <Field label="Points for a draw" required error={errs.pointsDraw}>
              {({ id }) => (
                <Input id={id} inputMode="numeric" value={d.pointsDraw} onChange={(e) => setD({ ...d, pointsDraw: e.target.value })} />
              )}
            </Field>
            <Field label="Points for a loss" required error={errs.pointsLoss}>
              {({ id }) => (
                <Input id={id} inputMode="numeric" value={d.pointsLoss} onChange={(e) => setD({ ...d, pointsLoss: e.target.value })} />
              )}
            </Field>
          </div>
        </section>
        <section className={styles.section} aria-labelledby="tb-title">
          <h2 id="tb-title" className="t-h2">
            Tie-breakers
          </h2>
          <p className="t-body-s muted" style={{ margin: 0 }}>
            Applied top to bottom to teams level on points. Head-to-head compares only the ties between the level teams. Teams still level
            after all of these are marked “=” and listed by name.
          </p>
          {errs.tiebreakers && (
            <p role="alert" className="t-body-s" style={{ color: 'var(--err)', margin: 0 }}>
              {errs.tiebreakers}
            </p>
          )}
          <ol style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
            {d.order.map((tb, i) => {
              const on = d.on.includes(tb);
              return (
                <li
                  key={tb}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '8px 12px',
                    border: '1px solid var(--line)',
                    borderRadius: 'var(--r-s)',
                    opacity: on ? 1 : 0.55,
                  }}
                >
                  <input
                    type="checkbox"
                    id={`tb-${tb}`}
                    checked={on}
                    onChange={(e) => setD({ ...d, on: e.target.checked ? [...d.on, tb] : d.on.filter((x) => x !== tb) })}
                  />
                  <label htmlFor={`tb-${tb}`} style={{ flex: 1 }}>
                    {on ? `${active.indexOf(tb) + 1}. ` : ''}
                    {TIEBREAKER_LABEL[tb]}
                  </label>
                  <Button
                    size="sm"
                    variant="ghost"
                    aria-label={`Move ${TIEBREAKER_LABEL[tb]} up`}
                    disabled={i === 0}
                    onClick={() => move(i, -1)}
                  >
                    ↑
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    aria-label={`Move ${TIEBREAKER_LABEL[tb]} down`}
                    disabled={i === d.order.length - 1}
                    onClick={() => move(i, 1)}
                  >
                    ↓
                  </Button>
                </li>
              );
            })}
          </ol>
        </section>
      </fieldset>
      {!canEdit && <p className="t-caption">You can view these rules; only users with standings or settings permission can change them.</p>}
      <SaveBar
        dirty={dirty}
        saving={save.isPending}
        onDiscard={() => {
          setD(initial);
          setErrs({});
        }}
      />
    </form>
  );
}
