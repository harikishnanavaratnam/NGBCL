'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Select, Textarea } from '@/design-system/primitives/Inputs';
import { Tabs } from '@/design-system/primitives/Tabs';
import { REJECTION_REASONS, useRegistrationSummary, type NoteView, type RegistrationStatus } from '@/lib/api/registration';
import { formatAuditTime } from '@/lib/format/dates';
import s from './Registrations.module.css';

export const STATUS_OPTIONS: { value: RegistrationStatus | 'ALL'; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ALL', label: 'All' },
];

export function InboxHeader({ tab }: { tab: 'players' | 'teams' | 'duplicates' }) {
  const { data } = useRegistrationSummary();
  return (
    <div className={s.page} style={{ gap: 12 }}>
      <div className={s.head}>
        <div className={s.titleRow}>
          <span className="eyebrow">Registrations</span>
          <h1 className="t-h1">{tab === 'duplicates' ? 'Possible duplicates' : 'Registrations'}</h1>
        </div>
        <span className="muted" style={{ fontSize: 13 }}>
          Keyboard: <kbd className="kbd">J</kbd>/<kbd className="kbd">K</kbd> move · <kbd className="kbd">Enter</kbd> open ·{' '}
          <kbd className="kbd">A</kbd> approve · <kbd className="kbd">R</kbd> reject
        </span>
      </div>
      <Tabs
        label="Registration type"
        value={tab}
        items={[
          { value: 'players', label: 'Players', href: '/admin/registrations/players', count: data?.pendingPlayers },
          { value: 'teams', label: 'Teams', href: '/admin/registrations/teams', count: data?.pendingTeams },
          { value: 'duplicates', label: 'Duplicates', href: '/admin/registrations/duplicates', count: data?.flaggedPlayers },
        ]}
      />
    </div>
  );
}

/** Debounced search value (300 ms) so typing doesn't fire a request per key. */
export function useDebounced<T>(value: T, ms = 300) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

/** J/K/Enter/A/R shortcuts; ignored while typing in a field or when a modal owns focus. */
export function useInboxKeys(handlers: Partial<Record<'j' | 'k' | 'enter' | 'a' | 'r', () => void>>, enabled = true) {
  useEffect(() => {
    if (!enabled) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      const t = e.target as HTMLElement | null;
      if (t && (t.closest('input, textarea, select, [contenteditable="true"]') || t.closest('[role="dialog"][data-modal="true"]'))) return;
      const key = e.key === 'Enter' ? 'enter' : (e.key.toLowerCase() as 'j' | 'k' | 'a' | 'r');
      const h = handlers[key];
      if (h) {
        e.preventDefault();
        h();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [handlers, enabled]);
}

export function RejectForm({
  onReject,
  busy,
  focusKey,
}: {
  onReject: (reason: string, message?: string) => Promise<unknown>;
  busy: boolean;
  focusKey: number;
}) {
  const [reason, setReason] = useState('');
  const [other, setOther] = useState('');
  const [message, setMessage] = useState('');
  const [err, setErr] = useState<string>();
  const finalReason = reason === 'Other' ? other.trim() : reason;
  return (
    <div className={s.box}>
      <Field label="Rejection reason" required error={err}>
        {(a) => (
          <Select
            id={a.id}
            aria-describedby={a.describedBy}
            value={reason}
            // R shortcut focuses the reason (focusKey changes)
            key={focusKey}
            autoFocus={focusKey > 0}
            onChange={(e) => {
              setReason(e.target.value);
              setErr(undefined);
            }}
          >
            <option value="">Choose a reason</option>
            {REJECTION_REASONS.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </Select>
        )}
      </Field>
      {reason === 'Other' && (
        <Field label="Reason" required>
          {(a) => <input id={a.id} className="inp" value={other} maxLength={120} onChange={(e) => setOther(e.target.value)} />}
        </Field>
      )}
      <Field label="Message to the applicant" optional help="Kept with the registration; sent if notifications are switched on.">
        {(a) => <Textarea id={a.id} rows={2} maxLength={500} value={message} onChange={(e) => setMessage(e.target.value)} />}
      </Field>
      <div>
        <Button
          variant="danger"
          loading={busy}
          onClick={async () => {
            if (!finalReason) {
              setErr('Choose a reason — it’s kept in the history.');
              return;
            }
            await onReject(finalReason, message.trim() || undefined);
          }}
        >
          Reject (R)
        </Button>
      </div>
    </div>
  );
}

export function NotesPanel({ notes, onAdd, busy }: { notes: NoteView[]; onAdd: (body: string) => Promise<unknown>; busy: boolean }) {
  const [body, setBody] = useState('');
  return (
    <div className={s.section}>
      <Field label="Internal note" help="Never shown publicly.">
        {(a) => <Textarea id={a.id} rows={2} maxLength={1000} value={body} onChange={(e) => setBody(e.target.value)} />}
      </Field>
      <div>
        <Button
          size="sm"
          variant="out"
          loading={busy}
          disabled={!body.trim()}
          onClick={async () => {
            await onAdd(body.trim());
            setBody('');
          }}
        >
          Add note
        </Button>
      </div>
      {notes.length > 0 && (
        <div className={s.history}>
          {notes.map((n) => (
            <span key={n.id}>
              <span className="mono muted">{formatAuditTime(n.createdAt)}</span>
              <span>
                <strong>{n.authorName}:</strong> {n.body}
              </span>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

export function History({ items }: { items: { at: string | null; text: ReactNode }[] }) {
  return (
    <div className={s.section}>
      <span className="eyebrow">History</span>
      <div className={s.history}>
        {items
          .filter((i) => i.at)
          .map((i, idx) => (
            <span key={idx}>
              <span className="mono muted">{formatAuditTime(i.at!)}</span>
              <span>{i.text}</span>
            </span>
          ))}
      </div>
    </div>
  );
}

/** "In queue" age, e.g. 3 h, 2 d. */
export function queueAge(iso: string) {
  const ms = Date.now() - new Date(iso).getTime();
  const h = Math.floor(ms / 3_600_000);
  if (h < 1) return `${Math.max(1, Math.floor(ms / 60_000))} min`;
  if (h < 48) return `${h} h`;
  return `${Math.floor(h / 24)} d`;
}
