import clsx from 'clsx';
import type { CSSProperties, ReactNode } from 'react';
import { formatAmount, formatCompact, formatRs } from '@/lib/format/money';

/** Uppercase label / eyebrow ("ROUND 2 · COURT 1"). */
export function Eyebrow({ children, className, style }: { children: ReactNode; className?: string; style?: CSSProperties }) {
  return (
    <span className={clsx('eyebrow', className)} style={style}>
      {children}
    </span>
  );
}

/**
 * Placeholder for official data that isn't configured yet (dates, venue, team count…).
 * Visible dashed gold tag — never mistaken for real data (export `.tbc`).
 */
export function Tbc({ children }: { children: ReactNode }) {
  return <span className="tbc">{children}</span>;
}

/** Shows `value` when present, otherwise a TBC tag. */
export function OrTbc({ value, label }: { value: ReactNode | null | undefined; label: string }) {
  return value === null || value === undefined || value === '' ? <Tbc>[{label} TBC]</Tbc> : <>{value}</>;
}

type MoneyVariant = 'public' | 'admin' | 'compact';

/**
 * Money is a hero (brief §4): tabular figures, currency as a small label prefix in admin,
 * "Rs. 300,000" on public pages, compact "1.25M" only on the big auction display.
 */
export function Money({ value, variant = 'admin', className }: { value: number; variant?: MoneyVariant; className?: string }) {
  if (variant === 'public') return <span className={clsx('num', className)}>{formatRs(value)}</span>;
  if (variant === 'compact') return <span className={clsx('num', className)}>{formatCompact(value)}</span>;
  return (
    <span className={clsx('num', className)} style={{ whiteSpace: 'nowrap' }}>
      <span className="eyebrow" style={{ fontSize: '0.62em', marginRight: 4 }}>
        LKR
      </span>
      {formatAmount(value)}
    </span>
  );
}

/** Scoreboard numerals (Barlow Condensed 700, tabular). */
export function Score({ children, size = 48, dim, className }: { children: ReactNode; size?: number; dim?: boolean; className?: string }) {
  return (
    <span className={clsx('t-score', className)} style={{ fontSize: size, lineHeight: 0.9, opacity: dim ? 0.6 : 1 }}>
      {children}
    </span>
  );
}

/** References, bid numbers, audit timestamps. */
export function Mono({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={clsx('mono', className)}>{children}</span>;
}

/** 1 px court line with service-box corner ticks (brief §5 primitive). */
export function CourtDivider({ className }: { className?: string }) {
  return (
    <div className={className} role="separator" style={{ position: 'relative', height: 9 }}>
      <hr className="court" style={{ position: 'absolute', top: 4, left: 0, right: 0 }} />
      <span style={{ position: 'absolute', left: 0, top: 0, width: 1, height: 9, background: 'var(--line-3)' }} />
      <span style={{ position: 'absolute', right: 0, top: 0, width: 1, height: 9, background: 'var(--line-3)' }} />
    </div>
  );
}

/** "Tharshan SIVAKUMAR" — first name regular, surname heavy. */
export function PlayerName({ first, last, className }: { first: string; last: string; className?: string }) {
  return (
    <span className={className}>
      {first && <span style={{ fontWeight: 400 }}>{first} </span>}
      <strong style={{ fontWeight: 700, textTransform: 'uppercase' }}>{last}</strong>
    </span>
  );
}
