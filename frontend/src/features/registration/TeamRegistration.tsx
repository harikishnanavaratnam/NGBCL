'use client';

import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Controller, useForm, useWatch, type FieldPath } from 'react-hook-form';
import { Turnstile, type TurnstileInstance } from '@marsidev/react-turnstile';
import { ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, PhoneInput, Textarea } from '@/design-system/primitives/Inputs';
import { Tbc } from '@/design-system/primitives/Text';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { RegistrationSession, checkTeamName, toE164Lk, useTeamForm, type TeamForm } from '@/lib/api/registration';
import { formatDateTime } from '@/lib/format/dates';
import { formatRs } from '@/lib/format/money';
import { CropUpload, type PickedImage } from './CropUpload';
import { Declaration, ErrorSummary, HStepper, RegBand, RegShell, ReviewBlock, VStepper, stepStatus } from './Shell';
import { ClosedScreen, ConfirmationScreen, NotOpenScreen } from './StateScreens';
import s from './Registration.module.css';

interface Values {
  teamName: string;
  description: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  authorised: boolean;
  feeAcknowledged: boolean;
  consent: boolean;
}

const STEPS = [{ label: 'Team identity' }, { label: 'Contact person' }, { label: 'Review and consent' }];
const FIELD_STEP: Record<string, number> = {
  teamName: 0,
  description: 0,
  logoAssetId: 0,
  contactName: 1,
  contactEmail: 1,
  contactPhone: 1,
  authorised: 2,
  feeAcknowledged: 2,
  consent: 2,
};
const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]+\.[^\s@]{2,}$/;
const PHONE_RE = /^\+?[0-9]{9,15}$/;

type NameState = { name: string; state: 'idle' | 'checking' | 'available' | 'taken' | 'error' };

export function TeamRegistration() {
  const { data: form, isLoading, error, refetch } = useTeamForm();
  const [receipt, setReceipt] = useState<string | null>(null);

  if (isLoading) {
    return (
      <RegShell>
        <RegBand eyebrow="NGBCL 1.0 · Team registration" title="Enter a team" />
        <div className={clsx(s.wrap, s.body)}>
          <Skeleton height={420} />
        </div>
      </RegShell>
    );
  }
  if (error || !form) {
    return (
      <RegShell>
        <RegBand eyebrow="NGBCL 1.0 · Team registration" title="Enter a team" />
        <div className={clsx(s.wrap, s.body)}>
          <ErrorState title="Couldn’t load the form" message={errorMessage(error)} onRetry={() => void refetch()} />
        </div>
      </RegShell>
    );
  }
  if (receipt) {
    return (
      <RegShell>
        <ConfirmationScreen
          reference={receipt}
          who="team"
          steps={[
            { title: 'Received', body: 'Your team details and logo are with the organisers.' },
            { title: 'Review and entry fee', body: 'We check your logo and name, then contact you about the entry fee.' },
            { title: 'Approved and at the auction', body: 'Approved teams appear on the website and bid for players at the auction.' },
          ]}
        />
      </RegShell>
    );
  }
  const w = form.window;
  if (w.state === 'NOT_OPEN' || w.state === 'NOT_CONFIGURED') {
    return (
      <RegShell>
        <NotOpenScreen
          who="Team"
          opensAt={w.opensAt}
          needs={[
            'Your team name',
            'A square team logo (PNG with transparent background works best)',
            'The contact person’s phone number and email',
          ]}
        />
      </RegShell>
    );
  }
  if (w.state === 'CLOSED') {
    return (
      <RegShell>
        <ClosedScreen who="Team" closedAt={w.closesAt} />
      </RegShell>
    );
  }
  return <TeamWizard form={form} onDone={setReceipt} onWindowChanged={() => void refetch()} />;
}

function useNameCheck(name: string): NameState {
  const [state, setState] = useState<NameState>({ name: '', state: 'idle' });
  const trimmed = name.trim().replace(/\s+/g, ' ');
  useEffect(() => {
    if (trimmed.length < 3) return;
    const ctrl = new AbortController();
    const t = setTimeout(async () => {
      setState({ name: trimmed, state: 'checking' });
      try {
        const r = await checkTeamName(trimmed, ctrl.signal);
        setState({ name: trimmed, state: r.available ? 'available' : 'taken' });
      } catch {
        if (!ctrl.signal.aborted) setState({ name: trimmed, state: 'error' });
      }
    }, 400);
    return () => {
      clearTimeout(t);
      ctrl.abort();
    };
  }, [trimmed]);
  return trimmed.length < 3 || state.name !== trimmed ? { name: trimmed, state: trimmed.length < 3 ? 'idle' : 'checking' } : state;
}

function TeamWizard({ form, onDone, onWindowChanged }: { form: TeamForm; onDone: (ref: string) => void; onWindowChanged: () => void }) {
  const session = useMemo(() => new RegistrationSession('TEAM'), []);
  const turnstileRef = useRef<TurnstileInstance>(undefined);
  const [step, setStep] = useState(0);
  const [maxReached, setMaxReached] = useState(0);
  const [logo, setLogo] = useState<PickedImage | null>(null);
  const [logoError, setLogoError] = useState<string>();
  const [botToken, setBotToken] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string>();
  const [submitting, setSubmitting] = useState(false);
  const [triedSubmit, setTriedSubmit] = useState(false);

  const {
    register,
    control,
    trigger,
    getValues,
    setError,
    getFieldState,
    formState: { errors, isDirty },
  } = useForm<Values>({
    mode: 'onBlur',
    defaultValues: {
      teamName: '',
      description: '',
      contactName: '',
      contactEmail: '',
      contactPhone: '',
      authorised: false,
      feeAcknowledged: false,
      consent: false,
    },
  });
  const v = useWatch({ control });
  /** Once a field shows an error, re-check it as the user types (so the error clears before they click on). */
  const reg = <N extends FieldPath<Values>>(name: N, rules?: Parameters<typeof register<N>>[1]) =>
    register(name, {
      ...rules,
      onChange: () => {
        if (getFieldState(name).error) void trigger(name);
      },
    });
  const nameCheck = useNameCheck(v.teamName ?? '');
  const fee = form.entryFee;
  const feeLabel = fee ? formatRs(fee) : null;

  const stepFields: FieldPath<Values>[][] = [
    ['teamName', 'description'],
    ['contactName', 'contactEmail', 'contactPhone'],
    ['authorised', 'feeAcknowledged', 'consent'],
  ];

  const go = (i: number) => {
    setStep(i);
    setMaxReached((m) => Math.max(m, i));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const validateStep = async (i: number) => {
    let ok = await trigger(stepFields[i], { shouldFocus: true });
    if (i === 0) {
      if (nameCheck.state === 'taken') {
        setError('teamName', { message: 'Already taken — try another name.' });
        ok = false;
      }
      if (!logo) {
        setLogoError('Add your team logo to continue.');
        ok = false;
      }
    }
    return ok;
  };

  const next = async () => {
    if (step === 2) return submit();
    if (await validateStep(step)) go(step + 1);
  };

  const submit = async () => {
    setTriedSubmit(true);
    setSubmitError(undefined);
    for (let i = 0; i < 2; i++) {
      if (!(await validateStep(i))) return go(i);
    }
    if (!(await validateStep(2))) return;
    if (form.turnstileSiteKey && !botToken) {
      setSubmitError('Complete the security check.');
      return;
    }
    const x = getValues();
    setSubmitting(true);
    try {
      const receipt = await session.submit('team', {
        teamName: x.teamName,
        description: x.description || null,
        logoAssetId: logo!.id,
        contactName: x.contactName,
        contactEmail: x.contactEmail,
        contactPhone: toE164Lk(x.contactPhone),
        authorised: x.authorised,
        feeAcknowledged: x.feeAcknowledged,
        consent: x.consent,
        turnstileToken: botToken,
      });
      onDone(receipt.referenceNumber);
    } catch (e) {
      turnstileRef.current?.reset();
      setBotToken(null);
      if (e instanceof ApiError) {
        if (e.code === 'VALIDATION_FAILED' && Object.keys(e.fieldErrors).length) {
          let first = 2;
          for (const [field, message] of Object.entries(e.fieldErrors)) {
            if (field === 'logoAssetId') setLogoError(message);
            else setError(field as FieldPath<Values>, { message });
            first = Math.min(first, FIELD_STEP[field] ?? 2);
          }
          go(first);
          return;
        }
        if (e.code === 'REGISTRATION_SESSION_EXPIRED' || (e.status === 404 && e.code === 'NOT_FOUND')) {
          session.reset();
          setLogo(null);
          setLogoError('Your form session expired — please add your logo again. Your other answers are kept.');
          go(0);
          return;
        }
        if (e.code === 'REGISTRATION_CLOSED' || e.code === 'REGISTRATION_NOT_OPEN') {
          onWindowChanged();
          return;
        }
      }
      setSubmitError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  const reviewErrors = triedSubmit
    ? [
        ...(errors.authorised ? [{ id: 'decl-auth', label: 'Confirm you are authorised to register this team' }] : []),
        ...(errors.feeAcknowledged ? [{ id: 'decl-fee', label: 'Confirm the entry fee notice' }] : []),
        ...(errors.consent ? [{ id: 'decl-consent', label: 'Confirm the privacy notice' }] : []),
        ...(submitError ? [{ id: 'bot-check', label: submitError }] : []),
      ]
    : [];

  const nameMsg = nameCheck.state === 'available' ? 'Name available' : nameCheck.state === 'checking' ? undefined : undefined;
  const nameErr = errors.teamName?.message ?? (nameCheck.state === 'taken' ? 'Already taken — try another name.' : undefined);

  return (
    <RegShell dirty={isDirty || Boolean(logo)}>
      <RegBand eyebrow="NGBCL 1.0 · Team registration" title="Enter a team">
        <div className={s.meta}>
          <span>Own a franchise and build your squad at the auction</span>
          <span className="muted">No account needed · about 3 minutes · have your logo ready</span>
          {form.window.closesAt && <span className="muted">Closes {formatDateTime(form.window.closesAt)}</span>}
        </div>
      </RegBand>

      <div className={clsx(s.wrap, s.body)}>
        <div className={s.grid}>
          <VStepper steps={STEPS} current={step} maxReached={maxReached} onGo={go} />
          <div className={s.main}>
            <HStepper steps={STEPS} current={step} />
            <form
              className={clsx('card', s.formCard)}
              noValidate
              onSubmit={(e) => {
                e.preventDefault();
                void next();
              }}
            >
              <AnimatePresence mode="wait" initial={false}>
                <motion.div
                  key={step}
                  initial={{ opacity: 0, x: 12 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -12 }}
                  transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                >
                  {step === 0 && (
                    <div className={s.formPad}>
                      <div className={s.stepHead}>
                        <h2>Team identity</h2>
                        <span className="help">How your team appears on the website, the auction screen and fixtures.</span>
                      </div>
                      <div className={s.notice}>
                        <svg
                          width="22"
                          height="22"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="var(--gold)"
                          strokeWidth="2"
                          strokeLinecap="round"
                          aria-hidden="true"
                          style={{ flexShrink: 0 }}
                        >
                          <circle cx="12" cy="12" r="9" />
                          <path d="M12 11v6M12 7h.01" />
                        </svg>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                          <strong>
                            Team entry fee: <span className="num">{feeLabel ?? <Tbc>[FEE TBC]</Tbc>}</span>
                          </strong>
                          <span>The organisers will contact you about payment after review. Nothing is paid on this website.</span>
                        </div>
                      </div>
                      <Field
                        label="Team name"
                        required
                        error={nameErr}
                        success={!nameErr ? nameMsg : undefined}
                        help={
                          nameCheck.state === 'checking' && (v.teamName ?? '').trim().length >= 3
                            ? 'Checking…'
                            : 'Checked live as you type. Names must be unique in NGBCL 1.0.'
                        }
                      >
                        {(a) => (
                          <Input
                            id={a.id}
                            aria-describedby={a.describedBy}
                            aria-invalid={a.invalid}
                            maxLength={40}
                            autoComplete="organization"
                            {...reg('teamName', {
                              validate: (x) => {
                                const n = x.trim().replace(/\s+/g, ' ');
                                return (n.length >= 3 && n.length <= 40) || 'Team names are 3–40 characters.';
                              },
                            })}
                          />
                        )}
                      </Field>
                      <Field
                        label="Short description"
                        optional
                        error={errors.description?.message}
                        help={
                          <span style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                            <span>Public on your team page once approved</span>
                            <span className="mono">{(v.description ?? '').length} / 300</span>
                          </span>
                        }
                      >
                        {(a) => (
                          <Textarea
                            id={a.id}
                            aria-describedby={a.describedBy}
                            rows={3}
                            maxLength={300}
                            placeholder="Where the team is from, its story, its spirit"
                            {...reg('description')}
                          />
                        )}
                      </Field>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <span className="lbl">
                          Team logo <span className="req">*</span>
                        </span>
                        <CropUpload
                          variant="logo"
                          rules={form.logo}
                          session={session}
                          value={logo}
                          error={logoError}
                          onChange={(l) => {
                            setLogo(l);
                            setLogoError(undefined);
                          }}
                        />
                        <span className="help">
                          Organisers verify the logo before approval. The crest-tile ring colour is assigned by the organisers from the team
                          palette.
                        </span>
                      </div>
                    </div>
                  )}

                  {step === 1 && (
                    <div className={s.formPad}>
                      <div className={s.stepHead}>
                        <h2>Contact person</h2>
                        <span className="help">The team owner or the person the organisers should talk to.</span>
                      </div>
                      <div className={s.privacy} style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
                        <svg
                          width="18"
                          height="18"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          aria-hidden="true"
                          style={{ flexShrink: 0 }}
                        >
                          <rect x="5" y="11" width="14" height="9" rx="2" />
                          <path d="M8 11V8a4 4 0 0 1 8 0v3" />
                        </svg>
                        <span>
                          <strong>These details are private.</strong> They are never shown on the website — only the organisers see them.
                        </span>
                      </div>
                      <Field label="Full name" required error={errors.contactName?.message}>
                        {(a) => (
                          <Input
                            id={a.id}
                            aria-describedby={a.describedBy}
                            aria-invalid={a.invalid}
                            autoComplete="name"
                            maxLength={120}
                            {...reg('contactName', {
                              validate: (x) => x.trim().length >= 2 || 'Enter the contact person’s full name.',
                            })}
                          />
                        )}
                      </Field>
                      <div className={s.row2}>
                        <Field label="Email address" required error={errors.contactEmail?.message}>
                          {(a) => (
                            <Input
                              id={a.id}
                              aria-describedby={a.describedBy}
                              aria-invalid={a.invalid}
                              type="email"
                              autoComplete="email"
                              placeholder="name@example.com"
                              {...reg('contactEmail', {
                                validate: (x) => EMAIL_RE.test(x.trim()) || 'Enter an email like name@example.com',
                              })}
                            />
                          )}
                        </Field>
                        <Field label="Contact number" required error={errors.contactPhone?.message}>
                          {(a) => (
                            <PhoneInput
                              id={a.id}
                              aria-describedby={a.describedBy}
                              aria-invalid={a.invalid}
                              placeholder="7X XXX XXXX"
                              {...reg('contactPhone', {
                                validate: (x) => PHONE_RE.test(toE164Lk(x)) || 'Enter a phone number like 77 123 4567',
                              })}
                            />
                          )}
                        </Field>
                      </div>
                      <span className="help">We use these to confirm the entry fee and send team updates.</span>
                    </div>
                  )}

                  {step === 2 && (
                    <div className={s.formPad}>
                      <div className={s.stepHead}>
                        <h2>Review and submit</h2>
                        <span className="help">Check your details. You cannot edit them after submitting.</span>
                      </div>
                      <ErrorSummary items={reviewErrors} />
                      <ReviewBlock
                        title="Team identity"
                        onEdit={() => go(0)}
                        rows={[
                          { k: 'Team name', v: v.teamName || '—' },
                          {
                            k: 'Description',
                            v: v.description ? `${v.description.slice(0, 80)}${v.description.length > 80 ? '…' : ''}` : '—',
                          },
                          {
                            k: 'Logo',
                            v: logo ? (
                              <span style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                                <CrestTile logoUrl={logo.previewUrl} name={v.teamName || 'Team'} size="s" />
                                {logo.width} × {logo.height} px
                              </span>
                            ) : (
                              'Missing'
                            ),
                          },
                        ]}
                      />
                      <ReviewBlock
                        title="Contact person"
                        onEdit={() => go(1)}
                        rows={[
                          { k: 'Name', v: v.contactName || '—' },
                          { k: 'Email', v: v.contactEmail || '—' },
                          { k: 'Phone', v: v.contactPhone ? `+94 ${v.contactPhone}` : '—' },
                        ]}
                      />
                      <div className={s.privacy}>
                        <strong>Privacy notice</strong>
                        <span>
                          Team name, logo and description appear publicly once approved. Contact details stay private to the organisers.{' '}
                          <a href="/privacy" target="_blank" rel="noopener noreferrer">
                            Read the full notice
                          </a>
                        </span>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                        <Controller
                          control={control}
                          name="authorised"
                          rules={{ validate: (x) => x || 'Required to submit' }}
                          render={({ field, fieldState }) => (
                            <Declaration
                              id="decl-auth"
                              checked={field.value}
                              onChange={(x) => (field.onChange(x), void trigger(field.name))}
                              error={fieldState.error?.message}
                            >
                              I am authorised to register this team and the details are true.
                            </Declaration>
                          )}
                        />
                        <Controller
                          control={control}
                          name="feeAcknowledged"
                          rules={{ validate: (x) => x || 'Required to submit' }}
                          render={({ field, fieldState }) => (
                            <Declaration
                              id="decl-fee"
                              checked={field.value}
                              onChange={(x) => (field.onChange(x), void trigger(field.name))}
                              error={fieldState.error?.message}
                            >
                              I understand the {feeLabel ?? 'team'} entry fee is arranged with the organisers after review.
                            </Declaration>
                          )}
                        />
                        <Controller
                          control={control}
                          name="consent"
                          rules={{ validate: (x) => x || 'Required to submit' }}
                          render={({ field, fieldState }) => (
                            <Declaration
                              id="decl-consent"
                              checked={field.value}
                              onChange={field.onChange}
                              error={fieldState.error?.message}
                            >
                              I have read the privacy notice.
                            </Declaration>
                          )}
                        />
                      </div>
                      {form.turnstileSiteKey && (
                        <div id="bot-check">
                          <Turnstile
                            ref={turnstileRef}
                            siteKey={form.turnstileSiteKey}
                            options={{ theme: 'dark', size: 'flexible' }}
                            onSuccess={setBotToken}
                            onExpire={() => setBotToken(null)}
                            onError={() => setBotToken(null)}
                          />
                        </div>
                      )}
                    </div>
                  )}
                </motion.div>
              </AnimatePresence>
              <div className={s.actions}>
                {step > 0 ? (
                  <Button variant="out" onClick={() => go(step - 1)} disabled={submitting}>
                    Back
                  </Button>
                ) : (
                  <span className={clsx('help', s.hideS)}>Progress is kept if an upload fails</span>
                )}
                <Button variant="volt" type="submit" loading={submitting}>
                  {step === 2 ? 'Submit application' : 'Save and continue'}
                </Button>
              </div>
            </form>
          </div>

          <aside className={clsx('card', s.side)} aria-label="Your team card">
            <span className="eyebrow">Your team card</span>
            <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              <CrestTile logoUrl={logo?.previewUrl} name={v.teamName || 'Your team'} size="m" />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
                <strong style={{ overflowWrap: 'anywhere' }}>{v.teamName || 'Your team name'}</strong>
                <span className="help">
                  {v.description ? `${v.description.slice(0, 60)}${v.description.length > 60 ? '…' : ''}` : 'Short description'}
                </span>
              </div>
            </div>
            <div style={{ borderTop: '1px solid var(--line)', paddingTop: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {STEPS.map((st, i) => (
                <div key={st.label} className={s.sideRow}>
                  <span>{st.label}</span>
                  <span className="muted">{stepStatus(i, step, maxReached)}</span>
                </div>
              ))}
            </div>
            <div className={s.next}>
              <strong>What happens next</strong>
              <span className="muted">
                Organisers check your name and logo, then contact you about the entry fee. Approved teams join the auction.
              </span>
            </div>
          </aside>
        </div>
      </div>
    </RegShell>
  );
}
