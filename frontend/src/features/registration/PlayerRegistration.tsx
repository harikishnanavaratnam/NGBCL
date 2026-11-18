'use client';

import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { useMemo, useRef, useState } from 'react';
import { Controller, useForm, useWatch, type FieldPath } from 'react-hook-form';
import { Turnstile, type TurnstileInstance } from '@marsidev/react-turnstile';
import { ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, PhoneInput, Textarea } from '@/design-system/primitives/Inputs';
import { RadioCards } from '@/design-system/primitives/RadioCard';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { RegistrationSession, toE164Lk, usePlayerForm, type PlayerForm } from '@/lib/api/registration';
import { formatDate, formatDateTime } from '@/lib/format/dates';
import { splitName } from '@/lib/format/names';
import { CropUpload, type PickedImage } from './CropUpload';
import { Declaration, ErrorSummary, HStepper, RegBand, RegShell, ReviewBlock, VStepper, stepStatus } from './Shell';
import { ClosedScreen, ConfirmationScreen, NotOpenScreen } from './StateScreens';
import s from './Registration.module.css';

interface Values {
  fullName: string;
  dateOfBirth: string;
  gender: '' | 'MALE' | 'FEMALE';
  email: string;
  phone: string;
  address: string;
  homeArea: string;
  experience: string;
  emergencyName: string;
  emergencyPhone: string;
  skillCategoryId: string;
  jaffnaDeclared: boolean;
  consent: boolean;
}

const STEPS = [{ label: 'Personal details' }, { label: 'Photo' }, { label: 'Playing profile' }, { label: 'Review and consent' }];

/** Server field → wizard step, so a rejected submit jumps to the right place. */
const FIELD_STEP: Record<string, number> = {
  fullName: 0,
  dateOfBirth: 0,
  gender: 0,
  email: 0,
  phone: 0,
  address: 0,
  homeArea: 0,
  photoAssetId: 1,
  skillCategoryId: 2,
  experience: 2,
  emergencyName: 2,
  emergencyPhone: 2,
  jaffnaDeclared: 3,
  consent: 3,
};

/** Jaffna district divisions — suggestions only; free text is accepted. */
const AREAS = [
  'Jaffna Town',
  'Nallur',
  'Kopay',
  'Chavakachcheri',
  'Point Pedro',
  'Karaveddy',
  'Vadamarachchi East',
  'Chankanai',
  'Sandilipay',
  'Uduvil',
  'Tellippalai',
  'Karainagar',
  'Kayts',
  'Velanai',
  'Delft',
];

const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]+\.[^\s@]{2,}$/;
const PHONE_RE = /^\+?[0-9]{9,15}$/;

function minAgeDate() {
  const d = new Date();
  d.setFullYear(d.getFullYear() - 8);
  return d.toISOString().slice(0, 10);
}

export function PlayerRegistration() {
  const { data: form, isLoading, error, refetch } = usePlayerForm();
  const [receipt, setReceipt] = useState<string | null>(null);

  if (isLoading) {
    return (
      <RegShell>
        <RegBand eyebrow="NGBCL 1.0 · Player registration" title="Register as a player" />
        <div className={clsx(s.wrap, s.body)}>
          <Skeleton height={420} />
        </div>
      </RegShell>
    );
  }
  if (error || !form) {
    return (
      <RegShell>
        <RegBand eyebrow="NGBCL 1.0 · Player registration" title="Register as a player" />
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
          who="player"
          steps={[
            { title: 'Received', body: 'Your details and photo are with the organisers.' },
            { title: 'Review', body: 'We check eligibility and your photo. We may contact you if something is missing.' },
            { title: 'Approved and in the auction', body: 'Approved players appear on the website and enter the auction pool.' },
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
          who="Player"
          opensAt={w.opensAt}
          needs={[
            'A clear, recent photo of your face (JPEG, PNG or WebP)',
            'Your contact number and email address',
            'Your home area in Jaffna',
          ]}
        />
      </RegShell>
    );
  }
  if (w.state === 'CLOSED') {
    return (
      <RegShell>
        <ClosedScreen who="Player" closedAt={w.closesAt} />
      </RegShell>
    );
  }
  return <PlayerWizard form={form} onDone={setReceipt} onWindowChanged={() => void refetch()} />;
}

function PlayerWizard({ form, onDone, onWindowChanged }: { form: PlayerForm; onDone: (ref: string) => void; onWindowChanged: () => void }) {
  const f = form.fields;
  const session = useMemo(() => new RegistrationSession('PLAYER'), []);
  const turnstileRef = useRef<TurnstileInstance>(undefined);
  const [step, setStep] = useState(0);
  const [maxReached, setMaxReached] = useState(0);
  const [photo, setPhoto] = useState<PickedImage | null>(null);
  const [photoError, setPhotoError] = useState<string>();
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
      fullName: '',
      dateOfBirth: '',
      gender: '',
      email: '',
      phone: '',
      address: '',
      homeArea: '',
      experience: '',
      emergencyName: '',
      emergencyPhone: '',
      skillCategoryId: '',
      jaffnaDeclared: false,
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

  const stepFields: FieldPath<Values>[][] = [
    [
      'fullName',
      'email',
      'phone',
      ...(f.dateOfBirth.collect ? (['dateOfBirth'] as const) : []),
      ...(f.gender.collect ? (['gender'] as const) : []),
      ...(f.address.collect ? (['address'] as const) : []),
      ...(f.homeArea.collect ? (['homeArea'] as const) : []),
    ],
    [],
    [
      'skillCategoryId',
      ...(f.experience.collect ? (['experience'] as const) : []),
      ...(f.emergencyContact.collect ? (['emergencyName', 'emergencyPhone'] as const) : []),
    ],
    ['jaffnaDeclared', 'consent'],
  ];

  const go = (i: number) => {
    setStep(i);
    setMaxReached((m) => Math.max(m, i));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const validateStep = async (i: number) => {
    const ok = await trigger(stepFields[i], { shouldFocus: true });
    if (i === 1 && !photo) {
      setPhotoError('Add your player photo to continue.');
      return false;
    }
    return ok;
  };

  const next = async () => {
    if (step === 3) return submit();
    if (await validateStep(step)) go(step + 1);
  };

  const submit = async () => {
    setTriedSubmit(true);
    setSubmitError(undefined);
    for (let i = 0; i < 3; i++) {
      if (!(await validateStep(i))) return go(i);
    }
    if (!(await validateStep(3))) return;
    if (form.turnstileSiteKey && !botToken) {
      setSubmitError('Complete the security check.');
      return;
    }
    const x = getValues();
    setSubmitting(true);
    try {
      const receipt = await session.submit('player', {
        fullName: x.fullName,
        dateOfBirth: f.dateOfBirth.collect && x.dateOfBirth ? x.dateOfBirth : null,
        gender: f.gender.collect && x.gender ? x.gender : null,
        email: x.email,
        phone: toE164Lk(x.phone),
        address: f.address.collect ? x.address : null,
        homeArea: f.homeArea.collect ? x.homeArea : null,
        experience: f.experience.collect ? x.experience : null,
        emergencyName: f.emergencyContact.collect ? x.emergencyName : null,
        emergencyPhone: f.emergencyContact.collect && x.emergencyPhone ? toE164Lk(x.emergencyPhone) : null,
        skillCategoryId: x.skillCategoryId || null,
        photoAssetId: photo!.id,
        jaffnaDeclared: x.jaffnaDeclared,
        consent: x.consent,
        turnstileToken: botToken,
      });
      onDone(receipt.referenceNumber);
    } catch (e) {
      turnstileRef.current?.reset();
      setBotToken(null);
      if (e instanceof ApiError) {
        if (e.code === 'VALIDATION_FAILED' && Object.keys(e.fieldErrors).length) {
          let first = 3;
          for (const [field, message] of Object.entries(e.fieldErrors)) {
            if (field === 'photoAssetId') setPhotoError(message);
            else setError(field as FieldPath<Values>, { message });
            first = Math.min(first, FIELD_STEP[field] ?? 3);
          }
          go(first);
          return;
        }
        if (e.code === 'REGISTRATION_SESSION_EXPIRED' || (e.status === 404 && e.code === 'NOT_FOUND')) {
          session.reset();
          setPhoto(null);
          setPhotoError('Your form session expired — please add your photo again. Your other answers are kept.');
          go(1);
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
        ...(errors.jaffnaDeclared ? [{ id: 'decl-jaffna', label: 'Confirm your Jaffna eligibility' }] : []),
        ...(errors.consent ? [{ id: 'decl-consent', label: 'Confirm the privacy notice' }] : []),
        ...(submitError ? [{ id: 'bot-check', label: submitError }] : []),
      ]
    : [];

  const name = splitName(v.fullName ?? '');
  const categories = form.categories;
  const categoryName = categories.find((c) => c.id === v.skillCategoryId)?.name;
  const genderLabel = v.gender === 'MALE' ? 'Male' : v.gender === 'FEMALE' ? 'Female' : '—';

  return (
    <RegShell dirty={isDirty || Boolean(photo)}>
      <RegBand eyebrow="NGBCL 1.0 · Player registration" title="Register as a player">
        <div className={s.meta}>
          <span className="chip c-gold">Jaffna players only</span>
          <span>No account needed</span>
          <span className="muted">About 5 minutes · have a clear photo ready</span>
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
                        <h2>Personal details</h2>
                        <span className="help">
                          Fields marked <span className="req">*</span> are required. We only collect what the tournament needs.
                        </span>
                      </div>
                      <Field
                        label="Full name"
                        required
                        error={errors.fullName?.message}
                        help="Public: shown on your approved player profile."
                      >
                        {(a) => (
                          <Input
                            id={a.id}
                            aria-describedby={a.describedBy}
                            aria-invalid={a.invalid}
                            autoComplete="name"
                            placeholder="As it should appear on your player card"
                            maxLength={120}
                            {...reg('fullName', {
                              validate: (x) => x.trim().replace(/\s+/g, ' ').length >= 2 || 'Enter your full name.',
                            })}
                          />
                        )}
                      </Field>
                      {(f.dateOfBirth.collect || f.gender.collect) && (
                        <div className={s.row2}>
                          {f.dateOfBirth.collect && (
                            <Field
                              label="Date of birth"
                              required={f.dateOfBirth.required}
                              optional={!f.dateOfBirth.required}
                              error={errors.dateOfBirth?.message}
                              help="Private."
                            >
                              {(a) => (
                                <Input
                                  id={a.id}
                                  aria-describedby={a.describedBy}
                                  aria-invalid={a.invalid}
                                  type="date"
                                  autoComplete="bday"
                                  max={minAgeDate()}
                                  min="1936-01-01"
                                  {...reg('dateOfBirth', { required: f.dateOfBirth.required ? 'Enter your date of birth.' : false })}
                                />
                              )}
                            </Field>
                          )}
                          {f.gender.collect && (
                            <div className={clsx('field', errors.gender && 'err')}>
                              <span className="lbl">
                                Gender {f.gender.required ? <span className="req">*</span> : <span className="opt">(optional)</span>}
                              </span>
                              <div className={s.genders} role="radiogroup" aria-label="Gender">
                                {(['MALE', 'FEMALE'] as const).map((g) => (
                                  <label key={g} className={s.genderOpt}>
                                    <input
                                      type="radio"
                                      value={g}
                                      {...reg('gender', { required: f.gender.required ? 'Choose an option.' : false })}
                                    />
                                    {g === 'MALE' ? 'Male' : 'Female'}
                                  </label>
                                ))}
                              </div>
                              {errors.gender && (
                                <span className="errmsg" role="alert">
                                  {errors.gender.message}
                                </span>
                              )}
                              <span className="help">Used for match categories.</span>
                            </div>
                          )}
                        </div>
                      )}
                      <div className={s.row2}>
                        <Field label="Email address" required error={errors.email?.message} help="Private. Never shown on the website.">
                          {(a) => (
                            <Input
                              id={a.id}
                              aria-describedby={a.describedBy}
                              aria-invalid={a.invalid}
                              type="email"
                              autoComplete="email"
                              inputMode="email"
                              placeholder="name@example.com"
                              {...reg('email', { validate: (x) => EMAIL_RE.test(x.trim()) || 'Enter an email like name@example.com' })}
                            />
                          )}
                        </Field>
                        <Field label="Contact number" required error={errors.phone?.message} help="Private. Never shown on the website.">
                          {(a) => (
                            <PhoneInput
                              id={a.id}
                              aria-describedby={a.describedBy}
                              aria-invalid={a.invalid}
                              placeholder="7X XXX XXXX"
                              {...reg('phone', {
                                validate: (x) => PHONE_RE.test(toE164Lk(x)) || 'Enter a phone number like 77 123 4567',
                              })}
                            />
                          )}
                        </Field>
                      </div>
                      {f.address.collect && (
                        <Field
                          label="Address"
                          required={f.address.required}
                          optional={!f.address.required}
                          error={errors.address?.message}
                          help="Private."
                        >
                          {(a) => (
                            <Textarea
                              id={a.id}
                              aria-describedby={a.describedBy}
                              aria-invalid={a.invalid}
                              rows={2}
                              autoComplete="street-address"
                              placeholder="House, street, town"
                              maxLength={300}
                              {...reg('address', {
                                validate: (x) => !f.address.required || x.trim().length > 0 || 'Enter your address.',
                              })}
                            />
                          )}
                        </Field>
                      )}
                      {f.homeArea.collect && (
                        <Field
                          label="Home area"
                          required={f.homeArea.required}
                          optional={!f.homeArea.required}
                          error={errors.homeArea?.message}
                          help="Used to check Jaffna eligibility."
                        >
                          {(a) => (
                            <>
                              <Input
                                id={a.id}
                                aria-describedby={a.describedBy}
                                aria-invalid={a.invalid}
                                list="ngbcl-areas"
                                placeholder="Start typing your area"
                                maxLength={80}
                                {...reg('homeArea', {
                                  validate: (x) => !f.homeArea.required || x.trim().length > 0 || 'Enter your home area.',
                                })}
                              />
                              <datalist id="ngbcl-areas">
                                {AREAS.map((ar) => (
                                  <option key={ar} value={ar} />
                                ))}
                              </datalist>
                            </>
                          )}
                        </Field>
                      )}
                    </div>
                  )}

                  {step === 1 && (
                    <div className={s.formPad}>
                      <div className={s.stepHead}>
                        <h2>Your player photo</h2>
                        <span className="help">This becomes your public player card and your auction photo, once approved.</span>
                      </div>
                      <CropUpload
                        variant="photo"
                        rules={form.photo}
                        session={session}
                        value={photo}
                        error={photoError}
                        onChange={(p) => {
                          setPhoto(p);
                          setPhotoError(undefined);
                        }}
                      />
                    </div>
                  )}

                  {step === 2 && (
                    <div className={s.formPad} style={{ gap: 24 }}>
                      <div className={s.stepHead}>
                        <h2>Playing profile</h2>
                        <span className="help">Help team owners know your game before the auction.</span>
                      </div>
                      {categories.length > 0 && (
                        <Controller
                          control={control}
                          name="skillCategoryId"
                          rules={{ required: form.categoryRequired ? 'Choose your category.' : false }}
                          render={({ field, fieldState }) => (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }} id="f-category">
                              <RadioCards
                                name="category"
                                legend={
                                  <>
                                    Skill category{' '}
                                    {form.categoryRequired ? <span className="req">*</span> : <span className="opt">(optional)</span>}
                                  </>
                                }
                                value={field.value || null}
                                onChange={(x) => (field.onChange(x), void trigger(field.name))}
                                options={categories.map((c) => ({ value: c.id, label: c.name, description: c.description ?? undefined }))}
                              />
                              {fieldState.error && (
                                <span className="errmsg" role="alert">
                                  {fieldState.error.message}
                                </span>
                              )}
                            </div>
                          )}
                        />
                      )}
                      {f.experience.collect && (
                        <Field
                          label="Playing experience"
                          required={f.experience.required}
                          optional={!f.experience.required}
                          error={errors.experience?.message}
                          help={
                            <span style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                              <span>Public on your player page once approved</span>
                              <span className="mono">{(v.experience ?? '').length} / 300</span>
                            </span>
                          }
                        >
                          {(a) => (
                            <Textarea
                              id={a.id}
                              aria-describedby={a.describedBy}
                              aria-invalid={a.invalid}
                              rows={4}
                              maxLength={300}
                              placeholder="Clubs, school or university teams, events played, preferred discipline"
                              {...reg('experience', {
                                validate: (x) => !f.experience.required || x.trim().length > 0 || 'Tell us about your playing experience.',
                              })}
                            />
                          )}
                        </Field>
                      )}
                      {f.emergencyContact.collect && (
                        <div
                          style={{
                            borderTop: '1px dashed var(--line-2)',
                            paddingTop: 20,
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 14,
                          }}
                        >
                          <span className="lbl">
                            Emergency contact{' '}
                            {f.emergencyContact.required ? <span className="req">*</span> : <span className="opt">(optional)</span>}
                          </span>
                          <div className={s.row2}>
                            <Field label="Name" error={errors.emergencyName?.message}>
                              {(a) => (
                                <Input
                                  id={a.id}
                                  aria-describedby={a.describedBy}
                                  aria-invalid={a.invalid}
                                  maxLength={120}
                                  {...reg('emergencyName', {
                                    validate: (x) => !f.emergencyContact.required || x.trim().length > 0 || 'Enter a name.',
                                  })}
                                />
                              )}
                            </Field>
                            <Field label="Phone" error={errors.emergencyPhone?.message}>
                              {(a) => (
                                <PhoneInput
                                  id={a.id}
                                  aria-describedby={a.describedBy}
                                  aria-invalid={a.invalid}
                                  {...reg('emergencyPhone', {
                                    validate: (x) =>
                                      !x.trim()
                                        ? !f.emergencyContact.required || 'Enter a phone number.'
                                        : PHONE_RE.test(toE164Lk(x)) || 'Enter a valid phone number.',
                                  })}
                                />
                              )}
                            </Field>
                          </div>
                          <span className="help">Private. Only organisers can see this.</span>
                        </div>
                      )}
                      {categories.length === 0 && !f.experience.collect && !f.emergencyContact.collect && (
                        <p className="muted">Nothing else needed here — continue to review.</p>
                      )}
                    </div>
                  )}

                  {step === 3 && (
                    <div className={s.formPad}>
                      <div className={s.stepHead}>
                        <h2>Review and submit</h2>
                        <span className="help">Check your details. You cannot edit them after submitting.</span>
                      </div>
                      <ErrorSummary items={reviewErrors} />
                      <ReviewBlock
                        title="Personal details"
                        onEdit={() => go(0)}
                        rows={[
                          { k: 'Full name', v: v.fullName || '—' },
                          ...(f.dateOfBirth.collect ? [{ k: 'Date of birth', v: v.dateOfBirth ? formatDate(v.dateOfBirth) : '—' }] : []),
                          { k: 'Email', v: v.email || '—' },
                          { k: 'Phone', v: v.phone ? `+94 ${v.phone}` : '—' },
                          ...(f.homeArea.collect ? [{ k: 'Home area', v: v.homeArea || '—' }] : []),
                          ...(f.gender.collect ? [{ k: 'Gender', v: genderLabel }] : []),
                        ]}
                      />
                      <ReviewBlock
                        title="Photo"
                        onEdit={() => go(1)}
                        rows={[
                          {
                            k: 'Photo',
                            v: photo ? (
                              <span style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                                {/* eslint-disable-next-line @next/next/no-img-element */}
                                <img src={photo.previewUrl} alt="" width={48} height={60} style={{ objectFit: 'cover', borderRadius: 4 }} />
                                <span>
                                  {photo.width} × {photo.height} px · checks passed
                                </span>
                              </span>
                            ) : (
                              'Missing'
                            ),
                          },
                        ]}
                      />
                      <ReviewBlock
                        title="Playing profile"
                        onEdit={() => go(2)}
                        rows={[
                          { k: 'Skill category', v: categoryName ?? 'Not selected' },
                          ...(f.experience.collect
                            ? [
                                {
                                  k: 'Experience',
                                  v: v.experience ? `${v.experience.slice(0, 80)}${v.experience.length > 80 ? '…' : ''}` : '—',
                                },
                              ]
                            : []),
                        ]}
                      />
                      <div className={s.privacy}>
                        <strong>Privacy notice</strong>
                        <span>
                          Your name, photo, skill category and team appear publicly once approved. Contact details, address, date of birth
                          and emergency contact stay private to the organisers.{' '}
                          <a href="/privacy" target="_blank" rel="noopener noreferrer">
                            Read the full notice
                          </a>
                        </span>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                        <Controller
                          control={control}
                          name="jaffnaDeclared"
                          rules={{ validate: (x) => x || 'Required to submit' }}
                          render={({ field, fieldState }) => (
                            <Declaration
                              id="decl-jaffna"
                              checked={field.value}
                              onChange={(x) => (field.onChange(x), void trigger(field.name))}
                              error={fieldState.error?.message}
                            >
                              <strong>I confirm I am eligible to play as a Jaffna player in NGBCL 1.0.</strong>
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
                              I have read the privacy notice and confirm the details I gave are true.
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
                  {step === 3 ? 'Submit registration' : 'Save and continue'}
                </Button>
              </div>
            </form>
          </div>

          <aside className={clsx('card', s.side)} aria-label="Your player card">
            <span className="eyebrow">Your player card</span>
            <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
              {photo ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={photo.previewUrl} alt="" style={{ width: 72, aspectRatio: '4 / 5', objectFit: 'cover', borderRadius: 4 }} />
              ) : (
                <div className="ph ph-person" style={{ width: 72, aspectRatio: '4 / 5', padding: 2, fontSize: 9 }}>
                  Photo
                </div>
              )}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
                <span style={{ overflowWrap: 'anywhere' }}>
                  {name.first || 'Your'} <strong style={{ textTransform: 'uppercase' }}>{name.last || 'name'}</strong>
                </span>
                {categoryName && (
                  <span style={{ alignSelf: 'flex-start' }}>
                    <StatusChip tone="neutral">{categoryName}</StatusChip>
                  </span>
                )}
                {v.homeArea && <span className="help">{v.homeArea}</span>}
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
                Organisers review your details and photo. Approved players appear on the site and enter the auction pool.
              </span>
            </div>
          </aside>
        </div>
      </div>
    </RegShell>
  );
}
