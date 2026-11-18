import { formatAuditTime } from '@/lib/format/dates';
import { Mono } from '../primitives/Text';

export interface AuditEntry {
  id: string;
  actorName: string;
  action: string;
  at: string;
  reference?: string;
  changes?: { field: string; from: string | null; to: string | null }[];
  reason?: string;
}

/** Actor, action, field diff (old → new), mono timestamp (brief §5). */
export function AuditTimeline({ entries }: { entries: AuditEntry[] }) {
  return (
    <ol style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column' }}>
      {entries.map((e) => (
        <li key={e.id} style={{ display: 'grid', gridTemplateColumns: '28px 1fr', gap: 12, padding: '10px 0', borderBottom: '1px solid var(--line)' }}>
          <span className="ph ph-person" style={{ width: 28, height: 28, borderRadius: '50%', padding: 0 }} aria-hidden="true" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 14 }}>
            <span>
              <strong>{e.actorName}</strong> {e.action} {e.reference && <Mono>{e.reference}</Mono>}
            </span>
            {e.changes?.map((c) => (
              <span key={c.field} className="t-body-s muted">
                {c.field}: <s>{c.from ?? '—'}</s> → <span style={{ color: 'var(--text)' }}>{c.to ?? '—'}</span>
              </span>
            ))}
            {e.reason && <span className="t-body-s muted">Reason: {e.reason}</span>}
            <Mono className="t-caption">{formatAuditTime(e.at)}</Mono>
          </div>
        </li>
      ))}
    </ol>
  );
}
