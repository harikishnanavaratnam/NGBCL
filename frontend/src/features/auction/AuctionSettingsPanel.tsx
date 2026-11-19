'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { Field } from '@/design-system/primitives/Field';
import { Input, Textarea, Toggle } from '@/design-system/primitives/Inputs';
import { Modal } from '@/design-system/primitives/Overlay';
import { Button } from '@/design-system/primitives/Button';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { useAuctionSettings, useSaveAuctionSettings, type AuctionSettings } from '@/lib/api/auction';
import { useAuth } from '@/lib/auth/session';
import { fromZonedInput, toZonedInput } from '@/lib/format/dates';
import { formatLkr } from '@/lib/format/money';
import { PanelError, PanelLoading } from '../settings/PanelStates';
import { SaveBar } from '../settings/SaveBar';
import styles from '../settings/Settings.module.css';
import { useSaveErrors } from '../settings/useSaveErrors';

const whole = (label: string, min = 1) =>
  z
    .string()
    .trim()
    .refine((v) => /^\d{1,12}$/.test(v.replace(/,/g, '')), `Enter ${label} as a whole number`)
    .refine((v) => Number(v.replace(/,/g, '')) >= min, `Must be at least ${min}`);

const schema = z
  .object({
    initialBudget: whole('the budget'),
    maxSquadSize: whole('the squad size').refine((v) => Number(v) <= 40, 'At most 40'),
    minSquadSize: z.string().trim().refine((v) => v === '' || /^\d{1,2}$/.test(v), 'Enter a number or leave empty'),
    defaultBasePrice: whole('the base price'),
    minIncrement: whole('the increment'),
    allowReauction: z.boolean(),
    allowManualAdjustment: z.boolean(),
    startsAt: z.string(),
  })
  .refine((v) => v.minSquadSize === '' || Number(v.minSquadSize) <= Number(v.maxSquadSize), {
    path: ['minSquadSize'],
    message: 'Can’t be more than the maximum squad size',
  })
  .refine((v) => Number(v.defaultBasePrice.replace(/,/g, '')) <= Number(v.initialBudget.replace(/,/g, '')), {
    path: ['defaultBasePrice'],
    message: 'Can’t be more than a team’s budget',
  });

type Values = z.infer<typeof schema>;

const n = (v: string) => Number(v.replace(/,/g, ''));

function toValues(s: AuctionSettings): Values {
  return {
    initialBudget: String(s.initialBudget),
    maxSquadSize: String(s.maxSquadSize),
    minSquadSize: s.minSquadSize === null ? '' : String(s.minSquadSize),
    defaultBasePrice: String(s.defaultBasePrice),
    minIncrement: String(s.minIncrement),
    allowReauction: s.allowReauction,
    allowManualAdjustment: s.allowManualAdjustment,
    startsAt: toZonedInput(s.startsAt),
  };
}

/** Admin › Settings › Auction (AUC-002..008). Everything is configurable; locked rules after the start. */
export function AuctionSettingsPanel() {
  const { data, isLoading, error, refetch } = useAuctionSettings();
  if (isLoading) return <PanelLoading />;
  if (error || !data) return <PanelError error={error} onRetry={() => void refetch()} />;
  return <AuctionSettingsForm settings={data} />;
}

function AuctionSettingsForm({ settings }: { settings: AuctionSettings }) {
  const toast = useToast();
  const qc = useQueryClient();
  const { can } = useAuth();
  const canEdit = can('AUCTION_CONFIGURE');
  const save = useSaveAuctionSettings();
  const defaults = useMemo(() => toValues(settings), [settings]);
  const [pending, setPending] = useState<Values | null>(null);
  const [reason, setReason] = useState('');
  const {
    register,
    handleSubmit,
    reset,
    setError,
    control,
    formState: { errors, isDirty },
  } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: defaults });
  const showErrors = useSaveErrors(setError);
  const onError = (e: unknown) => {
    void qc.invalidateQueries({ queryKey: ['auction', 'settings'] });
    showErrors(e);
  };

  useEffect(() => reset(defaults), [defaults, reset]);

  const submit = (v: Values, why?: string) =>
    new Promise<void>((resolve) =>
      save.mutate(
        {
          initialBudget: n(v.initialBudget),
          maxSquadSize: n(v.maxSquadSize),
          minSquadSize: v.minSquadSize === '' ? null : n(v.minSquadSize),
          defaultBasePrice: n(v.defaultBasePrice),
          minIncrement: n(v.minIncrement),
          allowReauction: v.allowReauction,
          allowManualAdjustment: v.allowManualAdjustment,
          startsAt: fromZonedInput(v.startsAt),
          version: settings.version,
          reason: why ?? null,
        },
        {
          onSuccess: () => {
            toast.success('Auction settings saved');
            setPending(null);
            setReason('');
            resolve();
          },
          onError: (e) => {
            onError(e);
            resolve();
          },
        },
      ),
    );

  const onSubmit = handleSubmit((v) => {
    if (settings.locked) setPending(v);
    else void submit(v);
  });

  const [budget, maxSquad, base, inc] = useWatch({ control, name: ['initialBudget', 'maxSquadSize', 'defaultBasePrice', 'minIncrement'] });
  const avg = n(maxSquad) > 0 ? n(budget) / n(maxSquad) : 0;

  return (
    <form className={styles.panel} onSubmit={onSubmit} noValidate>
      <fieldset disabled={!canEdit} style={{ border: 0, padding: 0, margin: 0, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 24 }}>
        <section className={styles.section} aria-labelledby="auc-title">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <h2 id="auc-title" className="t-h2">
              Auction
            </h2>
            {settings.locked ? <StatusChip tone="pending">Auction started · changes are audited</StatusChip> : <StatusChip tone="neutral">Editable until the auction starts</StatusChip>}
          </div>
          <p className="t-body-s muted" style={{ margin: 0 }}>
            These rules are enforced by the server for every bid. After the auction starts, the team budget is fixed (use a budget
            adjustment instead) and other changes need a reason and apply from the next bid.
          </p>
          <div className={styles.grid2}>
            <Field label="Budget per team (LKR)" required error={errors.initialBudget?.message} help={settings.locked ? 'Locked — the auction has started.' : undefined}>
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} disabled={settings.locked} {...register('initialBudget')} />}
            </Field>
            <Field label="Auction start" optional help="Shown on the display intro and the public auction page. Leave empty for “TBC”.">
              {({ id }) => <Input id={id} type="datetime-local" {...register('startsAt')} />}
            </Field>
          </div>
          <div className={styles.grid3}>
            <Field label="Maximum squad size" required error={errors.maxSquadSize?.message}>
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} {...register('maxSquadSize')} />}
            </Field>
            <Field
              label="Minimum squad size"
              optional
              error={errors.minSquadSize?.message}
              help="If set, a team must keep enough budget to reach it at the default base price."
            >
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} {...register('minSquadSize')} />}
            </Field>
            <Field label="Default base price (LKR)" required error={errors.defaultBasePrice?.message} help="Individual players can have their own base price on the setup page.">
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} {...register('defaultBasePrice')} />}
            </Field>
            <Field label="Minimum bid increment (LKR)" required error={errors.minIncrement?.message}>
              {({ id, describedBy }) => <Input id={id} inputMode="numeric" aria-describedby={describedBy} {...register('minIncrement')} />}
            </Field>
          </div>
          {avg > 0 && n(base) > 0 && n(inc) > 0 && (
            <p className="t-caption" style={{ margin: 0 }}>
              Average per player if a team fills its squad: {formatLkr(Math.round(avg))} · first bid {formatLkr(n(base))}, then +{formatLkr(n(inc))} per raise.
            </p>
          )}
          <div style={{ display: 'flex', gap: 24, flexWrap: 'wrap' }}>
            <Toggle id="reauc" label="Allow a re-auction round for unsold players" {...register('allowReauction')} />
            <Toggle id="adj" label="Allow manual budget adjustments (audited)" {...register('allowManualAdjustment')} />
          </div>
        </section>
      </fieldset>
      {!canEdit && <p className="t-caption">You can view these settings; only users with auction setup permission can change them.</p>}
      <SaveBar dirty={isDirty} saving={save.isPending} onDiscard={() => reset(defaults)} />

      <Modal
        open={pending !== null}
        onClose={() => setPending(null)}
        title="Change rules during the auction?"
        footer={
          <>
            <Button variant="out" onClick={() => setPending(null)} disabled={save.isPending}>
              Cancel
            </Button>
            <Button variant="volt" loading={save.isPending} disabled={reason.trim().length < 3} onClick={() => pending && void submit(pending, reason.trim())}>
              Save with reason
            </Button>
          </>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <p className="t-body muted" style={{ margin: 0 }}>
            The new rules apply from the next bid. Sales already made are not changed. The reason is saved in the audit log.
          </p>
          <Field label="Reason" required error={errors.root?.message}>
            {({ id }) => <Textarea id={id} rows={3} maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} />}
          </Field>
        </div>
      </Modal>
    </form>
  );
}
