'use client';

import clsx from 'clsx';
import { AnimatePresence, motion } from 'motion/react';
import { useMemo, useState } from 'react';
import { DataTable, type Column } from '@/design-system/data/DataTable';
import { ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input } from '@/design-system/primitives/Inputs';
import { ConfirmDialog, Drawer, Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { Segmented } from '@/design-system/primitives/Tabs';
import { useToast } from '@/design-system/primitives/Toast';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { useTeams } from '@/lib/api/directory';
import { errorMessage } from '@/lib/api/errors';
import {
  formatLkPhone,
  useApproveTeam,
  useNoteTeam,
  useRejectTeam,
  useTeamRegistration,
  useTeamRegistrations,
  useVerifyLogo,
  type RegistrationStatus,
  type TeamRegistrationDetail,
  type TeamRegistrationRow,
} from '@/lib/api/registration';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime } from '@/lib/format/dates';
import { ColorPicker } from '../directory/ColorPicker';
import { History, InboxHeader, NotesPanel, RejectForm, STATUS_OPTIONS, queueAge, useDebounced, useInboxKeys } from './shared';
import s from './Registrations.module.css';

export function TeamInbox() {
  const { can } = useAuth();
  const [status, setStatus] = useState<RegistrationStatus | 'ALL'>('PENDING');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [active, setActive] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const query = useDebounced(q);
  const { data, isLoading, error, refetch } = useTeamRegistrations({
    status: status === 'ALL' ? undefined : status,
    q: query || undefined,
    page,
    size,
  });
  const rows = data?.content;

  const move = (delta: number) => {
    if (!rows?.length) return;
    const ids = rows.map((r) => r.id);
    const cur = openId ?? active;
    const idx = cur ? ids.indexOf(cur) : -1;
    const next = ids[Math.min(ids.length - 1, Math.max(0, idx + delta))];
    setActive(next);
    if (openId) setOpenId(next);
  };
  const keys = useMemo(
    () => ({ j: () => move(1), k: () => move(-1), enter: () => active && setOpenId(active) }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [rows, active, openId],
  );
  useInboxKeys(keys, !openId);

  const columns: Column<TeamRegistrationRow>[] = [
    {
      key: 'ref',
      header: 'Reference',
      cell: (r) => (
        <span className="mono" style={{ whiteSpace: 'nowrap' }}>
          {r.registrationNumber}
        </span>
      ),
    },
    {
      key: 'logo',
      header: 'Logo',
      cell: (r) =>
        r.logoPreviewUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img className={clsx(s.thumb, s.thumbSq)} src={r.logoPreviewUrl} alt="" loading="lazy" />
        ) : (
          <span className={clsx(s.thumb, s.thumbSq)} />
        ),
    },
    { key: 'name', header: 'Team name', cell: (r) => <strong>{r.teamName}</strong> },
    { key: 'contact', header: 'Contact', cell: (r) => r.contactName },
    { key: 'sub', header: 'Submitted', cell: (r) => <span className="mono muted">{formatDateTime(r.submittedAt)}</span> },
    {
      key: 'age',
      header: 'In queue',
      cell: (r) => (r.status === 'PENDING' ? <span className="mono">{queueAge(r.submittedAt)}</span> : <span className="muted">—</span>),
    },
    {
      key: 'flags',
      header: 'Flags',
      cell: (r) => (
        <span className={s.flags}>
          {r.nameTaken && <StatusChip tone="error">Name taken</StatusChip>}
          {r.status === 'PENDING' && r.logoStatus !== 'VERIFIED' && (
            <StatusChip tone={r.logoStatus === 'REJECTED' ? 'error' : 'pending'}>
              {r.logoStatus === 'REJECTED' ? 'Logo rejected' : 'Logo unverified'}
            </StatusChip>
          )}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (r) => <StatusChip status={r.status}>{r.status.charAt(0) + r.status.slice(1).toLowerCase()}</StatusChip>,
    },
  ];

  return (
    <div className={s.page}>
      <InboxHeader tab="teams" />
      <section className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div className={s.filters}>
          <Segmented label="Status" value={status} onChange={(v) => (setStatus(v), setPage(0))} options={STATUS_OPTIONS} />
          <Input
            type="search"
            placeholder="Search team, contact or reference"
            aria-label="Search"
            value={q}
            onChange={(e) => (setQ(e.target.value), setPage(0))}
            style={{ marginLeft: 'auto' }}
          />
        </div>
        <DataTable
          caption="Team applications"
          columns={columns}
          rows={rows}
          rowKey={(r) => r.id}
          loading={isLoading}
          error={error}
          onRetry={() => void refetch()}
          activeKey={active ?? undefined}
          onRowClick={(r) => (setActive(r.id), setOpenId(r.id))}
          empty={{
            title: status === 'PENDING' ? 'No team applications waiting' : 'Nothing here',
            description: 'Team applications appear here as soon as they are submitted.',
          }}
          pagination={
            data
              ? {
                  page: data.page,
                  size: data.size,
                  totalElements: data.totalElements,
                  totalPages: data.totalPages,
                  onPage: setPage,
                  onSize: (n) => (setSize(n), setPage(0)),
                }
              : undefined
          }
        />
        <p className="help" style={{ margin: 0 }}>
          The entry fee is recorded offline in Directory → Teams after approval (no online payment).
        </p>
      </section>
      <TeamReviewDrawer
        id={openId}
        onClose={() => setOpenId(null)}
        onPrev={() => move(-1)}
        onNext={() => move(1)}
        canReview={can('REGISTRATION_REVIEW')}
      />
    </div>
  );
}

function TeamReviewDrawer({
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
  const { data, isLoading, error, refetch } = useTeamRegistration(id);
  const toast = useToast();
  const approve = useApproveTeam();
  const [confirm, setConfirm] = useState(false);
  const [rejectFocus, setRejectFocus] = useState(0);
  const [code, setCode] = useState<{ id: string; value: string } | null>(null);
  const [color, setColor] = useState<{ id: string; value: number | null } | null>(null);
  const pending = data?.status === 'PENDING';
  const canApprove = Boolean(canReview && pending && data && data.blockers.length === 0);
  const shortCode = code && data && code.id === data.id ? code.value : (data?.suggestedShortCode ?? '');
  const colorSlot = color && data && color.id === data.id ? color.value : null;

  const keys = useMemo(
    () => ({
      j: onNext,
      k: onPrev,
      a: () => canApprove && setConfirm(true),
      r: () => canReview && pending && setRejectFocus((n) => n + 1),
    }),
    [onNext, onPrev, canApprove, canReview, pending],
  );
  useInboxKeys(keys, Boolean(id) && !confirm);

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
            <span>{data.teamName}</span>
          </span>
        ) : (
          'Review team application'
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
            <Button variant="ok" disabled={!canApprove} onClick={() => setConfirm(true)}>
              Approve (A)
            </Button>
          </div>
        ) : undefined
      }
    >
      {isLoading && <Skeleton height={320} />}
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
            <TeamReviewBody
              d={data}
              canReview={canReview}
              rejectFocus={rejectFocus}
              shortCode={shortCode}
              onShortCode={(v) => setCode({ id: data.id, value: v })}
              colorSlot={colorSlot}
              onColor={(v) => setColor({ id: data.id, value: v })}
            />
          </motion.div>
        </AnimatePresence>
      )}
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title={`Approve ${data?.teamName ?? 'team'}?`}
        consequences={`Creates the team${shortCode ? ` (${shortCode.toUpperCase()})` : ''} and publishes its logo on the website. Record the entry fee in Directory → Teams when it's received.`}
        confirmLabel="Approve team"
        onConfirm={async () => {
          if (!data) return;
          try {
            await approve.mutateAsync({
              id: data.id,
              shortCode: shortCode.trim().toUpperCase() || undefined,
              colorSlot,
              version: data.version,
            });
            toast.success(`${data.teamName} approved`);
            onNext();
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}
      />
    </Drawer>
  );
}

function TeamReviewBody({
  d,
  canReview,
  rejectFocus,
  shortCode,
  onShortCode,
  colorSlot,
  onColor,
}: {
  d: TeamRegistrationDetail;
  canReview: boolean;
  rejectFocus: number;
  shortCode: string;
  onShortCode: (v: string) => void;
  colorSlot: number | null;
  onColor: (v: number | null) => void;
}) {
  const toast = useToast();
  const verify = useVerifyLogo();
  const reject = useRejectTeam();
  const note = useNoteTeam();
  const { data: teams } = useTeams();
  const [logoReject, setLogoReject] = useState<string | null>(null);
  const pending = d.status === 'PENDING';
  const usedColors = new Set((teams ?? []).map((t) => t.colorSlot).filter((x): x is number => x !== null));

  return (
    <div className={s.review}>
      <div className={s.revHead}>
        <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <StatusChip status={d.status}>{d.status.charAt(0) + d.status.slice(1).toLowerCase()}</StatusChip>
          {d.nameTaken && <StatusChip tone="error">Name already used by a team</StatusChip>}
        </span>
        {d.status === 'REJECTED' && (
          <span className="help">
            Rejected: <strong>{d.rejectionReason}</strong>
          </span>
        )}
      </div>
      <div className={s.revGrid}>
        <div className={s.section}>
          <span className="eyebrow">Submitted details</span>
          <div className={s.kv}>
            <span>Team name</span>
            <span>{d.teamName}</span>
            <span>Description</span>
            <span>{d.description ?? '—'}</span>
            <span>Contact</span>
            <span>{d.contactName}</span>
            <span>Email</span>
            <span>{d.contactEmail}</span>
            <span>Phone</span>
            <span>{formatLkPhone(d.contactPhone)}</span>
          </div>
          <div className={s.declared}>
            <StatusChip tone="ok">Declared</StatusChip>
            Authorised · entry fee notice read · consent given
          </div>
        </div>
        <div className={s.section}>
          <span className="eyebrow">Logo verification</span>
          {d.logo.previewUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img className={s.logo} src={d.logo.previewUrl} alt={`${d.teamName} logo`} />
          ) : (
            <div className="ph ph-crest" style={{ maxWidth: 200, aspectRatio: '1' }} />
          )}
          <span className="help mono">
            {d.logo.width} × {d.logo.height} px · {d.logo.contentType?.replace('image/', '').toUpperCase()}
          </span>
          {d.logo.previewUrl && (
            <div style={{ display: 'flex', gap: 10 }}>
              <CrestTile logoUrl={d.logo.previewUrl} name={d.teamName} colorSlot={colorSlot} size="m" />
            </div>
          )}
          <div className={s.verifyRow}>
            <strong style={{ fontSize: 14 }}>Verify logo</strong>
            <StatusChip
              tone={d.logo.verificationStatus === 'VERIFIED' ? 'ok' : d.logo.verificationStatus === 'REJECTED' ? 'error' : 'pending'}
            >
              {d.logo.verificationStatus === 'VERIFIED'
                ? 'Verified'
                : d.logo.verificationStatus === 'REJECTED'
                  ? 'Rejected'
                  : 'Not checked'}
            </StatusChip>
          </div>
          {canReview && pending && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {d.logo.verificationStatus !== 'VERIFIED' && (
                <Button
                  size="sm"
                  variant="ok"
                  loading={verify.isPending}
                  onClick={() =>
                    verify.mutateAsync({ id: d.id, approve: true }).then(
                      () => toast.success('Logo verified'),
                      (e) => toast.error(errorMessage(e)),
                    )
                  }
                >
                  Verify logo
                </Button>
              )}
              {d.logo.verificationStatus !== 'REJECTED' && (
                <Button size="sm" variant="out" onClick={() => setLogoReject('')}>
                  Reject logo…
                </Button>
              )}
            </div>
          )}
        </div>
      </div>

      {canReview && pending && (
        <div className={s.box}>
          <span className="eyebrow">On approval</span>
          <Field label="Short code" help="2–3 letters or digits, shown on scoreboards and fixtures.">
            {(a) => (
              <Input
                id={a.id}
                value={shortCode}
                maxLength={3}
                onChange={(e) => onShortCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
                style={{ maxWidth: 120 }}
              />
            )}
          </Field>
          <div className="field">
            <span className="lbl">Team colour</span>
            <ColorPicker value={colorSlot} onChange={onColor} used={usedColors} allowAuto />
            <span className="help">Leave on “Next free” to use the next unused palette slot.</span>
          </div>
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
            onReject={(reason, message) =>
              reject.mutateAsync({ id: d.id, reason, message, version: d.version }).then(
                () => toast.success(`${d.registrationNumber} rejected`),
                (e) => toast.error(errorMessage(e)),
              )
            }
          />
        </div>
      )}

      <History
        items={[
          { at: d.submittedAt, text: 'Submitted via public form' },
          ...(d.reviewedAt ? [{ at: d.reviewedAt, text: d.status === 'APPROVED' ? 'Approved — team created' : 'Rejected' }] : []),
        ]}
      />

      <Modal
        open={logoReject !== null}
        onClose={() => setLogoReject(null)}
        title="Reject logo"
        footer={
          <>
            <Button variant="out" onClick={() => setLogoReject(null)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={verify.isPending}
              disabled={!logoReject?.trim()}
              onClick={() =>
                verify.mutateAsync({ id: d.id, approve: false, reason: logoReject!.trim() }).then(
                  () => setLogoReject(null),
                  (e) => toast.error(errorMessage(e)),
                )
              }
            >
              Reject logo
            </Button>
          </>
        }
      >
        <Field label="Why doesn’t the logo meet the rules?" required>
          {(a) => <Input id={a.id} value={logoReject ?? ''} maxLength={255} onChange={(e) => setLogoReject(e.target.value)} />}
        </Field>
      </Modal>
    </div>
  );
}
