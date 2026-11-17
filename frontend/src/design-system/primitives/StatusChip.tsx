import clsx from 'clsx';
import type { ReactNode } from 'react';

/**
 * One meaning per status (brief §3). Every chip carries a word — never colour alone.
 * Live chips add a pulsing dot (disabled under prefers-reduced-motion by the DS).
 */
export type ChipTone =
  | 'live'
  | 'sold'
  | 'gold'
  | 'neutral'
  | 'ok'
  | 'pending'
  | 'error'
  | 'provisional'
  | 'navy';

const toneClass: Record<ChipTone, string> = {
  live: 'c-live',
  sold: 'c-sold',
  gold: 'c-gold',
  neutral: 'c-neutral',
  ok: 'c-ok',
  pending: 'c-pend',
  error: 'c-err',
  provisional: 'c-prov',
  navy: 'c-navy',
};

/** Domain statuses → tone, so screens never pick colours ad hoc. */
export const STATUS_TONE: Record<string, ChipTone> = {
  PENDING: 'pending',
  APPROVED: 'ok',
  CONFIRMED: 'ok',
  VERIFIED: 'ok',
  REJECTED: 'error',
  FAILED: 'error',
  LIVE: 'live',
  OPEN: 'live',
  IN_PROGRESS: 'live',
  PROVISIONAL: 'provisional',
  SUBMITTED: 'provisional',
  SOLD: 'sold',
  UNSOLD: 'neutral',
  WITHDRAWN: 'neutral',
  INACTIVE: 'neutral',
  DRAFT: 'neutral',
  ARCHIVED: 'neutral',
  UPCOMING: 'navy',
  ACTIVE: 'ok',
  COMPLETED: 'navy',
};

interface Props {
  tone?: ChipTone;
  /** Domain status — picks the tone and the label if children are omitted. */
  status?: string;
  dot?: boolean;
  children?: ReactNode;
  className?: string;
}

export function StatusChip({ tone, status, dot, children, className }: Props) {
  const t = tone ?? (status ? (STATUS_TONE[status] ?? 'neutral') : 'neutral');
  const label = children ?? status?.replace(/_/g, ' ');
  const showDot = dot ?? t === 'live';
  return (
    <span className={clsx('chip', toneClass[t], className)}>
      {showDot && <span className="dot" aria-hidden="true" />}
      {label}
    </span>
  );
}
