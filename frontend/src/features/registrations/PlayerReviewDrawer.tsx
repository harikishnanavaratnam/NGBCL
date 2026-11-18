'use client';

import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select, Textarea } from '@/design-system/primitives/Inputs';
import { ConfirmDialog, Drawer, Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { useCategories } from '@/lib/api/categories';
import { ApiError, errorMessage } from '@/lib/api/errors';
import {
  formatLkPhone,
  useApprovePlayer,
  useEditPlayer,
  useNotePlayer,
  usePlayerRegistration,
  useRejectPlayer,
  useResolveDuplicate,
  useVerifyPhoto,
  type DuplicateAction,
  type DuplicateView,
  type PlayerRegistrationDetail,
} from '@/lib/api/registration';
import { formatDate, formatDateTime } from '@/lib/format/dates';
import { History, NotesPanel, RejectForm, useInboxKeys } from './shared';
import s from './Registrations.module.css';

const GENDER: Record<string, string> = { MALE: 'Male', FEMALE: 'Female' };

export function PlayerReviewDrawer({
  id,
  onClose,
  onPrev,
  onNext,
  canReview,
}: {
  id: string | null;
  onClose: () => void;
  onPrev: () => void;
  onNext: () => void;
  canReview: boolean;
}) {
  const { data, isLoading, error, refetch } = usePlayerRegistration(id);
  const toast = useToast();
  const approve = useApprovePlayer();
  const [confirmApprove, setConfirmApprove] = useState(false);
  const [rejectFocus, setRejectFocus] = useState(0);
  const pending = data?.status === 'PENDING';
  const canApprove = Boolean(canReview && pending && data && data.blockers.length === 0);

  const keys = useMemo(
    () => ({
      j: onNext,
      k: onPrev,
      a: () => canApprove && setConfirmApprove(true),
      r: () => canReview && pending && setRejectFocus((n) => n + 1),
    }),
    [onNext, onPrev, canApprove, canReview, pending],
  );
  useInboxKeys(keys, Boolean(id) && !confirmApprove);

  return (
    <Drawer
      open={Boolean(id)}
      onClose={onClose}
      size={720}
      title={
        data ? (
          <span style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <span className="mono muted" style={{ fontSize: 12, fontWeight: 500 }}>
              {data.registrationNumber} · submitted {formatDateTime(data.submittedAt)}
            </span>
            <span>{data.fullName}</span>
          </span>
        ) : (
          'Review registration'
        )
      }
      footer={
        data && canReview && pending ? (
          <div className={s.footer}>
            <span className={clsx('help', s.hint)}>{data.blockers[0]?.message ?? 'Ready to approve.'}</span>
            <Button variant="out" size="sm" onClick={onPrev} aria-label="Previous (K)">
              ↑
            </Button>
            <Button variant="out" size="sm" onClick={onNext} aria-label="Next (J)">
              ↓
            </Button>
            <Button variant="ok" disabled={!canApprove} onClick={() => setConfirmApprove(true)}>
              Approve (A)
            </Button>
          </div>
        ) : undefined
      }
    >
      {isLoading && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <Skeleton height={28} width={240} />
          <Skeleton height={260} />
          <Skeleton height={120} />
        </div>
      )}
      {error && <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />}
      {data && (
        <AnimatePresence mode="wait">
          <motion.div
            key={data.id}
            initial={{ opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
          >
            <ReviewBody d={data} canReview={canReview} rejectFocus={rejectFocus} />
          </motion.div>
        </AnimatePresence>
      )}
      <ConfirmDialog
        open={confirmApprove}
        onClose={() => setConfirmApprove(false)}
        title={`Approve ${data?.fullName ?? 'player'}?`}
        consequences={
          <>
            {data?.linkedPlayer ? `Links to the existing profile of ${data.linkedPlayer.fullName}. ` : 'Creates a new player profile. '}
            The player appears on the public site and joins the auction pool. The photo is published.
          </>
        }
        confirmLabel="Approve player"
        onConfirm={async () => {
          if (!data) return;
          try {
            await approve.mutateAsync({ id: data.id, version: data.version });
            toast.success(`${data.fullName} approved`);
            onNext();
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}
      />
    </Drawer>
  );
}

function ReviewBody({ d, canReview, rejectFocus }: { d: PlayerRegistrationDetail; canReview: boolean; rejectFocus: number }) {
  const toast = useToast();
  const verify = useVerifyPhoto();
  const reject = useRejectPlayer();
  const note = useNotePlayer();
  const [editing, setEditing] = useState(false);
  const [zoom, setZoom] = useState(false);
  const [photoReject, setPhotoReject] = useState<string | null>(null);
  const pending = d.status === 'PENDING';
  const openDupes = d.duplicates.filter((x) => x.resolution === 'OPEN');

  return (
    <div className={s.review}>
      <div className={s.revHead}>
        <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <StatusChip status={d.status}>{d.status.charAt(0) + d.status.slice(1).toLowerCase()}</StatusChip>
          {d.duplicateFlag && <StatusChip tone="error">Possible duplicate</StatusChip>}
          {d.linkedPlayer && <StatusChip tone="navy">Linked to existing profile</StatusChip>}
        </span>
        {d.status === 'REJECTED' && (
          <span className="help">
            Rejected: <strong>{d.rejectionReason}</strong>
            {d.rejectionMessage ? ` — “${d.rejectionMessage}”` : ''}
          </span>
        )}
      </div>

      <div className={s.revGrid}>
        <div className={s.section}>
          <span className="eyebrow">Submitted details</span>
          {editing ? (
            <EditForm d={d} onDone={() => setEditing(false)} />
          ) : (
            <>
              <div className={s.kv}>
                <span>Full name</span>
                <span>{d.fullName}</span>
                <span>Date of birth</span>
                <span>{d.dateOfBirth ? formatDate(d.dateOfBirth) : '—'}</span>
                <span>Gender</span>
                <span>{d.gender ? (GENDER[d.gender] ?? d.gender) : '—'}</span>
                <span>Email</span>
                <span>{d.email}</span>
                <span>Phone</span>
                <span>{formatLkPhone(d.phone)}</span>
                <span>Address</span>
                <span>{d.address ?? '—'}</span>
                <span>Home area</span>
                <span>{d.homeArea ?? '—'}</span>
                <span>Skill category</span>
                <span>{d.categoryName ?? 'Not selected'}</span>
                <span>Experience</span>
                <span>{d.experience ?? '—'}</span>
                <span>Emergency</span>
                <span>{d.emergencyName ? `${d.emergencyName} · ${formatLkPhone(d.emergencyPhone)}` : '—'}</span>
              </div>
              <div className={s.declared}>
                <StatusChip tone="ok">Declared</StatusChip>
                Jaffna player eligibility · consent given
              </div>
              {canReview && pending && (
                <div>
                  <Button variant="out" size="sm" onClick={() => setEditing(true)}>
                    Edit details
                  </Button>
                  <span className="help" style={{ display: 'block', marginTop: 6 }}>
                    Edits are recorded in the audit history.
                  </span>
                </div>
              )}
            </>
          )}
        </div>

        <div className={s.section}>
          <span className="eyebrow">Photo verification</span>
          {d.photo.previewUrl ? (
            <button
              type="button"
              onClick={() => setZoom(true)}
              style={{ padding: 0, border: 0, background: 'none', alignSelf: 'flex-start' }}
              aria-label="Zoom photo"
            >
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img className={s.photo} src={d.photo.previewUrl} alt={`Photo of ${d.fullName}`} />
            </button>
          ) : (
            <div className="ph ph-person" style={{ maxWidth: 240, aspectRatio: '4 / 5' }}>
              No preview
            </div>
          )}
          <span className="help mono">
            {d.photo.width} × {d.photo.height} px · {d.photo.contentType?.replace('image/', '').toUpperCase()} ·{' '}
            {d.photo.size ? `${Math.round(d.photo.size / 1024)} KB` : '—'}
          </span>
          <div className={s.verifyRow}>
            <span style={{ display: 'flex', flexDirection: 'column' }}>
              <strong style={{ fontSize: 14 }}>Verify image</strong>
              <span className="help">Clear face, 4:5, meets size rules</span>
            </span>
            {d.photo.verificationStatus === 'VERIFIED' ? (
              <StatusChip tone="ok">Verified</StatusChip>
            ) : d.photo.verificationStatus === 'REJECTED' ? (
              <StatusChip tone="error">Rejected</StatusChip>
            ) : (
              <StatusChip tone="pending">Not checked</StatusChip>
            )}
          </div>
          {d.photo.rejectionReason && <span className="help">Photo rejected: {d.photo.rejectionReason}</span>}
          {canReview && pending && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {d.photo.verificationStatus !== 'VERIFIED' && (
                <Button
                  size="sm"
                  variant="ok"
                  loading={verify.isPending}
                  onClick={async () => {
                    try {
                      await verify.mutateAsync({ id: d.id, approve: true });
                      toast.success('Photo verified');
                    } catch (e) {
                      toast.error(errorMessage(e));
                    }
                  }}
                >
                  Verify photo
                </Button>
              )}
              {d.photo.verificationStatus !== 'REJECTED' && (
                <Button size="sm" variant="out" onClick={() => setPhotoReject('')}>
                  Reject photo…
                </Button>
              )}
            </div>
          )}
        </div>
      </div>

      {d.duplicates.length > 0 && (
        <div className={s.section}>
          <span className="eyebrow">Possible duplicates · never merged automatically</span>
          {d.duplicates.map((dup, i) => (
            <DuplicatePanel key={dup.id} d={d} dup={dup} index={i} total={d.duplicates.length} canReview={canReview && pending} />
          ))}
          {openDupes.length === 0 && <span className="help">All matches resolved.</span>}
        </div>
      )}

      {canReview && (
        <NotesPanel
          notes={d.notes}
          busy={note.isPending}
          onAdd={(body) => note.mutateAsync({ id: d.id, body }).catch((e) => toast.error(errorMessage(e)))}
        />
      )}

      {canReview && pending && (
        <div className={s.section}>
          <span className="eyebrow">Reject</span>
          <RejectForm
            focusKey={rejectFocus}
            busy={reject.isPending}
            onReject={async (reason, message) => {
              try {
                await reject.mutateAsync({ id: d.id, reason, message, version: d.version });
                toast.success(`${d.registrationNumber} rejected`);
              } catch (e) {
                toast.error(errorMessage(e));
              }
            }}
          />
        </div>
      )}

      <History
        items={[
          { at: d.submittedAt, text: 'Submitted via public form' },
          ...(d.duplicateFlag || d.duplicates.length ? [{ at: d.submittedAt, text: 'Flagged: possible duplicate (system)' }] : []),
          ...d.duplicates
            .filter((x) => x.resolvedAt)
            .map((x) => ({ at: x.resolvedAt, text: `Duplicate resolved: ${x.resolution.replace('_', ' ').toLowerCase()}` })),
          ...(d.reviewedAt ? [{ at: d.reviewedAt, text: d.status === 'APPROVED' ? 'Approved' : 'Rejected' }] : []),
        ]}
      />

      <Modal open={zoom} onClose={() => setZoom(false)} title={d.fullName} width={640}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        {d.photo.previewUrl && <img src={d.photo.previewUrl} alt={`Photo of ${d.fullName}`} style={{ width: '100%', borderRadius: 6 }} />}
      </Modal>
      <Modal
        open={photoReject !== null}
        onClose={() => setPhotoReject(null)}
        title="Reject photo"
        footer={
          <>
            <Button variant="out" onClick={() => setPhotoReject(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={verify.isPending}
              disabled={!photoReject?.trim()}
              onClick={async () => {
                try {
                  await verify.mutateAsync({ id: d.id, approve: false, reason: photoReject!.trim() });
                  setPhotoReject(null);
                } catch (e) {
                  toast.error(errorMessage(e));
                }
              }}
            >
              Reject photo
            </Button>
          </>
        }
      >
        <Field label="Why doesn’t the photo meet the rules?" required>
          {(a) => (
            <Input
              id={a.id}
              value={photoReject ?? ''}
              maxLength={255}
              onChange={(e) => setPhotoReject(e.target.value)}
              placeholder="e.g. Face not visible, group photo"
            />
          )}
        </Field>
      </Modal>
    </div>
  );
}

function DuplicatePanel({
  d,
  dup,
  index,
  total,
  canReview,
}: {
  d: PlayerRegistrationDetail;
  dup: DuplicateView;
  index: number;
  total: number;
  canReview: boolean;
}) {
  const toast = useToast();
  const resolve = useResolveDuplicate();
  const other = dup.player
    ? {
        label: `Existing profile${dup.player.inThisEdition ? ' · in NGBCL 1.0' : ''}`,
        name: dup.player.fullName,
        email: dup.player.email,
        phone: dup.player.phone,
        dob: dup.player.dateOfBirth,
        photo: dup.player.photoUrl,
        status: dup.player.inThisEdition ? 'APPROVED' : 'PROFILE',
      }
    : dup.registration
      ? {
          label: `Registration · ${dup.registration.registrationNumber}`,
          name: dup.registration.fullName,
          email: dup.registration.email,
          phone: dup.registration.phone,
          dob: dup.registration.dateOfBirth,
          photo: null,
          status: dup.registration.status,
        }
      : null;
  const on = dup.matchedOn.split(',');
  const act = async (action: DuplicateAction) => {
    try {
      await resolve.mutateAsync({ id: d.id, duplicateId: dup.id, action });
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : errorMessage(e));
    }
  };
  const cell = (match: boolean, value: string) => (
    <>
      {value} {match && <StatusChip tone="error">Match</StatusChip>}
    </>
  );
  return (
    <div className={clsx(s.box, dup.resolution === 'OPEN' && s.boxWarn)}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
        <strong>Possible duplicate — matched on {on.map((m) => m.replace('+', ' + ')).join(' and ')}</strong>
        <span className="help">
          {index + 1} of {total} · {dup.resolution === 'OPEN' ? 'Open' : dup.resolution.replace('_', ' ').toLowerCase()}
        </span>
      </div>
      {other && (
        <div className="scroll">
          <table className={clsx('tbl', s.cmp)}>
            <thead>
              <tr>
                <th>Field</th>
                <th>This registration</th>
                <th>
                  {other.label} {other.status !== 'PROFILE' && <StatusChip status={other.status}>{other.status.toLowerCase()}</StatusChip>}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="muted">Photo</td>
                <td>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  {d.photo.previewUrl ? <img className={s.thumb} src={d.photo.previewUrl} alt="" /> : '—'}
                </td>
                <td>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  {other.photo ? <img className={s.thumb} src={other.photo} alt="" /> : '—'}
                </td>
              </tr>
              <tr>
                <td className="muted">Full name</td>
                <td>{d.fullName}</td>
                <td>{cell(on.includes('name+dob'), other.name)}</td>
              </tr>
              <tr>
                <td className="muted">Email</td>
                <td>{d.email}</td>
                <td>{cell(on.includes('email'), other.email)}</td>
              </tr>
              <tr>
                <td className="muted">Phone</td>
                <td>{formatLkPhone(d.phone)}</td>
                <td>{cell(on.includes('phone'), formatLkPhone(other.phone))}</td>
              </tr>
              <tr>
                <td className="muted">Date of birth</td>
                <td>{d.dateOfBirth ? formatDate(d.dateOfBirth) : '—'}</td>
                <td>{other.dob ? formatDate(other.dob) : '—'}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
      {canReview && dup.resolution === 'OPEN' && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Button size="sm" variant="out" loading={resolve.isPending} onClick={() => act('LINK_EXISTING')}>
            Link to existing player
          </Button>
          <Button size="sm" variant="out" loading={resolve.isPending} onClick={() => act('NEW_PROFILE')}>
            Create new profile
          </Button>
          <Button size="sm" variant="out" loading={resolve.isPending} onClick={() => act('NOT_DUPLICATE')}>
            Not a duplicate
          </Button>
        </div>
      )}
    </div>
  );
}

interface EditValues {
  fullName: string;
  dateOfBirth: string;
  gender: string;
  email: string;
  phone: string;
  address: string;
  homeArea: string;
  experience: string;
  emergencyName: string;
  emergencyPhone: string;
  skillCategoryId: string;
}

function EditForm({ d, onDone }: { d: PlayerRegistrationDetail; onDone: () => void }) {
  const toast = useToast();
  const edit = useEditPlayer();
  const { data: cats } = useCategories();
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<EditValues>({
    defaultValues: {
      fullName: d.fullName,
      dateOfBirth: d.dateOfBirth ?? '',
      gender: d.gender ?? '',
      email: d.email,
      phone: d.phone,
      address: d.address ?? '',
      homeArea: d.homeArea ?? '',
      experience: d.experience ?? '',
      emergencyName: d.emergencyName ?? '',
      emergencyPhone: d.emergencyPhone ?? '',
      skillCategoryId: d.categoryId ?? '',
    },
  });
  const n = (v: string) => (v.trim() ? v.trim() : null);
  return (
    <form
      className={s.section}
      onSubmit={handleSubmit(async (v) => {
        try {
          await edit.mutateAsync({
            id: d.id,
            fullName: v.fullName,
            dateOfBirth: n(v.dateOfBirth),
            gender: n(v.gender),
            email: v.email,
            phone: v.phone,
            address: n(v.address),
            homeArea: n(v.homeArea),
            experience: n(v.experience),
            emergencyName: n(v.emergencyName),
            emergencyPhone: n(v.emergencyPhone),
            skillCategoryId: n(v.skillCategoryId),
            version: d.version,
          });
          toast.success('Details updated');
          onDone();
        } catch (e) {
          if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
            Object.entries(e.fieldErrors).forEach(([f, m]) => setError(f as keyof EditValues, { message: m }));
          } else toast.error(errorMessage(e));
        }
      })}
    >
      <Field label="Full name" required error={errors.fullName?.message}>
        {(a) => <Input id={a.id} {...register('fullName', { required: 'Enter the name.' })} />}
      </Field>
      <Field label="Date of birth" error={errors.dateOfBirth?.message}>
        {(a) => <Input id={a.id} type="date" {...register('dateOfBirth')} />}
      </Field>
      <Field label="Gender" error={errors.gender?.message}>
        {(a) => (
          <Select id={a.id} {...register('gender')}>
            <option value="">—</option>
            <option value="MALE">Male</option>
            <option value="FEMALE">Female</option>
          </Select>
        )}
      </Field>
      <Field label="Email" required error={errors.email?.message}>
        {(a) => <Input id={a.id} type="email" {...register('email', { required: 'Enter the email.' })} />}
      </Field>
      <Field label="Phone" required error={errors.phone?.message} help="With country code, e.g. +94771234567">
        {(a) => <Input id={a.id} type="tel" {...register('phone', { required: 'Enter the phone number.' })} />}
      </Field>
      <Field label="Address" error={errors.address?.message}>
        {(a) => <Textarea id={a.id} rows={2} {...register('address')} />}
      </Field>
      <Field label="Home area" error={errors.homeArea?.message}>
        {(a) => <Input id={a.id} {...register('homeArea')} />}
      </Field>
      <Field label="Skill category" error={errors.skillCategoryId?.message}>
        {(a) => (
          <Select id={a.id} {...register('skillCategoryId')}>
            <option value="">Not selected</option>
            {cats
              ?.filter((c) => c.active || c.id === d.categoryId)
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
          </Select>
        )}
      </Field>
      <Field label="Experience" error={errors.experience?.message}>
        {(a) => <Textarea id={a.id} rows={3} maxLength={300} {...register('experience')} />}
      </Field>
      <Field label="Emergency contact name" error={errors.emergencyName?.message}>
        {(a) => <Input id={a.id} {...register('emergencyName')} />}
      </Field>
      <Field label="Emergency phone" error={errors.emergencyPhone?.message}>
        {(a) => <Input id={a.id} type="tel" {...register('emergencyPhone')} />}
      </Field>
      <div style={{ display: 'flex', gap: 8 }}>
        <Button variant="out" onClick={onDone}>
          Cancel
        </Button>
        <Button variant="volt" type="submit" loading={edit.isPending}>
          Save changes
        </Button>
      </div>
    </form>
  );
}
