import { formatCompact } from '@/lib/format/money';

/** Spent (gold) vs remaining, with optional max-bid marker (brief §5 budget meter). */
export function BudgetMeter({ initial, spent, maxBid }: { initial: number; spent: number; maxBid?: number }) {
  const pct = initial > 0 ? Math.min(100, (spent / initial) * 100) : 0;
  const markerPct = maxBid !== undefined && initial > 0 ? Math.min(100, ((spent + maxBid) / initial) * 100) : null;
  const remaining = Math.max(0, initial - spent);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div
        className="bar"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={initial}
        aria-valuenow={spent}
        aria-label={`Spent ${spent} of ${initial}`}
        style={{ position: 'relative', overflow: 'hidden' }}
      >
        <span style={{ position: 'absolute', inset: 0, width: `${pct}%`, background: 'var(--gold)', borderRadius: 2 }} />
        {markerPct !== null && <span style={{ position: 'absolute', top: -2, bottom: -2, left: `${markerPct}%`, width: 2, background: 'var(--text)' }} />}
      </div>
      <span className="t-caption">
        Remaining <span className="num" style={{ color: 'var(--text)' }}>{formatCompact(remaining)}</span> of {formatCompact(initial)}
      </span>
    </div>
  );
}

/** N slots = configured max squad size; filled slots are gold. */
export function SquadSlots({ max, filled }: { max: number; filled: number }) {
  return (
    <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }} aria-label={`${filled} of ${max} squad slots filled`} role="img">
      {Array.from({ length: max }, (_, i) => (
        <span
          key={i}
          style={{
            width: 12,
            height: 12,
            borderRadius: 2,
            background: i < filled ? 'var(--gold)' : 'transparent',
            border: `1px solid ${i < filled ? 'var(--gold)' : 'var(--line-3)'}`,
          }}
        />
      ))}
    </div>
  );
}
