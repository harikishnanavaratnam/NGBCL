'use client';

import { useEffect, useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Field } from '@/design-system/primitives/Field';
import { Checkbox, Input, Toggle } from '@/design-system/primitives/Inputs';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { Tbc } from '@/design-system/primitives/Text';
import { useToast } from '@/design-system/primitives/Toast';
import { registrationPayload, useSaveRegistration, useSettings, type PlayerFieldRules, type Settings, type WindowState } from '@/lib/api/settings';
import { useAuth } from '@/lib/auth/session';
import { fromZonedInput, toZonedInput } from '@/lib/format/dates';
import { PanelError, PanelLoading } from './PanelStates';
import { SaveBar } from './SaveBar';
import styles from './Settings.module.css';
import { useSaveErrors } from './useSaveErrors';

interface Values {
  playerOpensAt: string;
  playerClosesAt: string;
  teamOpensAt: string;
  teamClosesAt: string;
  playerFields: PlayerFieldRules;
  dupMatchEmail: boolean;
  dupMatchPhone: boolean;
  dupMatchNameDob: boolean;
  maxPhotoMb: string;
  minPhotoWidth: string;
  minPhotoHeight: string;
  notifyOnRegistration: boolean;
}

const WINDOW_LABEL: Record<WindowState, { label: string; tone: 'neutral' | 'ok' | 'pending' | 'error' }> = {
  NOT_CONFIGURED: { label: 'Not set', tone: 'neutral' },
  NOT_OPEN: { label: 'Not open', tone: 'pending' },
  OPEN: { label: 'Open', tone: 'ok' },
  CLOSED: { label: 'Closed', tone: 'neutral' },
};

/** Configurable rows (export settings-reg). Fixed rows are always collected and required. */
const FIELD_ROWS: { key: keyof PlayerFieldRules; label: string; note?: string; publicOnProfile: string }[] = [
  { key: 'dateOfBirth', label: 'Date of birth', publicOnProfile: 'No' },
  { key: 'gender', label: 'Gender', publicOnProfile: 'Optional' },
  { key: 'address', label: 'Address', note: 'private', publicOnProfile: 'Never' },
  { key: 'homeArea', label: 'Home area', note: 'Jaffna eligibility', publicOnProfile: 'Optional' },
  { key: 'experience', label: 'Playing experience', publicOnProfile: 'Optional' },
  { key: 'emergencyContact', label: 'Emergency contact', note: 'private', publicOnProfile: 'Never' },
];
const FIXED_ROWS = [
  { label: 'Full name', publicOnProfile: 'Yes' },
  { label: 'Email and contact number', note: 'private', publicOnProfile: 'Never' },
  { label: 'Player photograph', note: 'always required', publicOnProfile: 'Yes' },
];

function toValues(s: Settings): Values {
  const r = s.registration;
  return {
    playerOpensAt: toZonedInput(r.playerOpensAt),
    playerClosesAt: toZonedInput(r.playerClosesAt),
    teamOpensAt: toZonedInput(r.teamOpensAt),
    teamClosesAt: toZonedInput(r.teamClosesAt),
    playerFields: r.playerFields,
    dupMatchEmail: r.dupMatchEmail,
    dupMatchPhone: r.dupMatchPhone,
    dupMatchNameDob: r.dupMatchNameDob,
    maxPhotoMb: String(Math.round((r.maxPhotoBytes / 1048576) * 10) / 10),
    minPhotoWidth: String(r.minPhotoWidth),
    minPhotoHeight: String(r.minPhotoHeight),
    notifyOnRegistration: r.notifyOnRegistration,
  };
}

export function RegistrationPanel() {
  const { data, isLoading, error, refetch } = useSettings();
  if (isLoading) return <PanelLoading />;
  if (error || !data) return <PanelError error={error} onRetry={() => void refetch()} />;
  return <RegistrationForm settings={data} />;
}

function WindowCard({ title, state, prefix, register, errors }: {
  title: string;
  state: WindowState;
  prefix: 'player' | 'team';
  register: ReturnType<typeof useForm<Values>>['register'];
  errors: Partial<Record<string, { message?: string }>>;
}) {
  const w = WINDOW_LABEL[state];
  return (
    <div className={styles.windowCard}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
        <strong>{title}</strong>
        <StatusChip tone={w.tone} dot={state === 'OPEN'}>
          {w.label}
        </StatusChip>
      </div>
      <div className={styles.grid2}>
        <Field label="Opens" error={errors[`${prefix}OpensAt`]?.message}>
          {({ id, describedBy }) => <Input id={id} type="datetime-local" aria-describedby={describedBy} {...register(`${prefix}OpensAt`)} />}
        </Field>
        <Field label="Closes" error={errors[`${prefix}ClosesAt`]?.message}>
          {({ id, describedBy }) => <Input id={id} type="datetime-local" aria-describedby={describedBy} {...register(`${prefix}ClosesAt`)} />}
        </Field>
      </div>
    </div>
  );
}

function RegistrationForm({ settings }: { settings: Settings }) {
  const toast = useToast();
  const { can } = useAuth();
  const canEdit = can('SETTINGS_MANAGE');
  const save = useSaveRegistration();
  const defaults = useMemo(() => toValues(settings), [settings]);
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<Values>({ defaultValues: defaults });
  const onError = useSaveErrors(setError);
  useEffect(() => reset(defaults), [defaults, reset]);

  const onSubmit = handleSubmit((v) => {
    let ok = true;
    for (const p of ['player', 'team'] as const) {
      const o = v[`${p}OpensAt`];
      const c = v[`${p}ClosesAt`];
      if (Boolean(o) !== Boolean(c)) {
        setError(`${p}${o ? 'ClosesAt' : 'OpensAt'}`, { message: 'Set both the opening and closing time' });
        ok = false;
      } else if (o && c && c <= o) {
        setError(`${p}ClosesAt`, { message: 'Must be after the opening time' });
        ok = false;
      }
    }
    if (!v.dupMatchEmail && !v.dupMatchPhone && !v.dupMatchNameDob) {
      setError('dupMatchEmail', { message: 'Keep at least one rule on' });
      ok = false;
    }
    const mb = Number(v.maxPhotoMb);
    if (!(mb >= 0.25 && mb <= 20)) {
      setError('maxPhotoMb', { message: 'Between 0.25 and 20 MB' });
      ok = false;
    }
    if (!ok) return;
    save.mutate(
      registrationPayload(settings, {
        playerOpensAt: fromZonedInput(v.playerOpensAt),
        playerClosesAt: fromZonedInput(v.playerClosesAt),
        teamOpensAt: fromZonedInput(v.teamOpensAt),
        teamClosesAt: fromZonedInput(v.teamClosesAt),
        playerFields: v.playerFields,
        dupMatchEmail: v.dupMatchEmail,
        dupMatchPhone: v.dupMatchPhone,
        dupMatchNameDob: v.dupMatchNameDob,
        maxPhotoBytes: Math.round(mb * 1048576),
        minPhotoWidth: Number(v.minPhotoWidth),
        minPhotoHeight: Number(v.minPhotoHeight),
        notifyOnRegistration: v.notifyOnRegistration,
      }),
      { onSuccess: () => toast.success('Registration settings saved'), onError },
    );
  });

  const status = settings.general.status;

  return (
    <form className={styles.panel} onSubmit={onSubmit} noValidate>
      <fieldset disabled={!canEdit} style={{ border: 0, padding: 0, margin: 0, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 24 }}>
        <section className={styles.section}>
          <h2 className="t-h2">Registration</h2>
          {status === 'DRAFT' && (
            <p className="chip c-pend" style={{ whiteSpace: 'normal', textTransform: 'none', letterSpacing: 0, fontSize: 13, padding: '8px 10px' }}>
              The tournament is still a draft, so registrations stay closed to the public until you move it to Upcoming (General tab).
            </p>
          )}
          <div className={styles.grid2}>
            <WindowCard title="Player registration" state={settings.registration.playerState} prefix="player" register={register} errors={errors} />
            <WindowCard title="Team registration" state={settings.registration.teamState} prefix="team" register={register} errors={errors} />
          </div>
          <span className="t-caption">Times are Sri Lanka time (UTC+05:30).</span>
        </section>

        <section className={styles.section}>
          <h3 className="t-h3">Player form fields</h3>
          <div className="scroll">
            <table className="tbl">
              <thead>
                <tr>
                  <th>Field</th>
                  <th className="c">Collect</th>
                  <th className="c">Required</th>
                  <th>Public on profile</th>
                </tr>
              </thead>
              <tbody>
                {FIXED_ROWS.map((r) => (
                  <tr key={r.label}>
                    <td>
                      {r.label} {r.note && <span className="t-caption">· {r.note}</span>}
                    </td>
                    <td className="c">
                      <input type="checkbox" role="switch" className="sw" checked disabled aria-label={`Collect ${r.label} (always)`} />
                    </td>
                    <td className="c">
                      <input type="checkbox" role="switch" className="sw" checked disabled aria-label={`${r.label} required (always)`} />
                    </td>
                    <td className="muted">{r.publicOnProfile}</td>
                  </tr>
                ))}
                {FIELD_ROWS.map((r) => (
                  <Controller
                    key={r.key}
                    control={control}
                    name={`playerFields.${r.key}`}
                    render={({ field }) => (
                      <tr>
                        <td>
                          {r.label} {r.note && <span className="t-caption">· {r.note}</span>}
                        </td>
                        <td className="c">
                          <input
                            type="checkbox"
                            role="switch"
                            className="sw"
                            aria-label={`Collect ${r.label}`}
                            checked={field.value.collect}
                            onChange={(e) => field.onChange({ collect: e.target.checked, required: e.target.checked && field.value.required })}
                          />
                        </td>
                        <td className="c">
                          <input
                            type="checkbox"
                            role="switch"
                            className="sw"
                            aria-label={`${r.label} required`}
                            checked={field.value.required}
                            disabled={!field.value.collect || !canEdit}
                            onChange={(e) => field.onChange({ ...field.value, required: e.target.checked })}
                          />
                        </td>
                        <td className="muted">{r.publicOnProfile}</td>
                      </tr>
                    )}
                  />
                ))}
              </tbody>
            </table>
          </div>
          <p className="t-caption">
            The photo is always required (BR-005). Contact fields can never be made public (PUB-008). Skill category is set in the Skill categories tab.
          </p>
        </section>

        <div className={styles.grid2}>
          <section className={styles.section}>
            <h3 className="t-h3">Duplicate matching on</h3>
            <Checkbox label="Email address" {...register('dupMatchEmail')} />
            <Checkbox label="Phone number" {...register('dupMatchPhone')} />
            <Checkbox label="Full name + date of birth" {...register('dupMatchNameDob')} />
            {errors.dupMatchEmail?.message && <span className="errmsg">{errors.dupMatchEmail.message}</span>}
            <span className="help">Possible duplicates are flagged for a person to resolve — never merged automatically.</span>
          </section>
          <section className={styles.section}>
            <h3 className="t-h3">Photo limits</h3>
            <div className={styles.grid3}>
              <Field label="Max size (MB)" error={errors.maxPhotoMb?.message}>
                {({ id, describedBy }) => <Input id={id} inputMode="decimal" aria-describedby={describedBy} {...register('maxPhotoMb')} />}
              </Field>
              <Field label="Min width (px)">
                {({ id }) => <Input id={id} inputMode="numeric" {...register('minPhotoWidth')} />}
              </Field>
              <Field label="Min height (px)">
                {({ id }) => <Input id={id} inputMode="numeric" {...register('minPhotoHeight')} />}
              </Field>
            </div>
            <span className="help">Photos are cropped to 4 : 5 in the form.</span>
          </section>
        </div>

        <section className={styles.section} style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
          <div>
            <strong>Send “registration received” notices</strong>
            <div className="t-caption">
              Channel <Tbc>[Email / SMS / WhatsApp TBC]</Tbc>
            </div>
          </div>
          <Toggle id="notify" label="" aria-label="Send registration received notices" {...register('notifyOnRegistration')} />
        </section>
      </fieldset>

      <SaveBar dirty={isDirty} saving={save.isPending} updatedAt={settings.updatedAt} onDiscard={() => reset(defaults)} />
    </form>
  );
}
