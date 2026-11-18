'use client';

import { motion } from 'motion/react';
import type { ReactNode } from 'react';
import type { ConnectionState } from '@/lib/realtime/stomp';
import { shake } from '../motion/presets';

const LABEL: Record<ConnectionState, string> = {
  live: 'Connected',
  connecting: 'Connecting…',
  reconnecting: 'Reconnecting…',
  offline: 'Offline',
};

/** Live / Reconnecting / Offline pill in the header of every real-time screen. */
export function ConnectionPill({ state }: { state: ConnectionState }) {
  const color = state === 'live' ? 'var(--lime)' : state === 'offline' ? 'var(--err)' : 'var(--warn)';
  return (
    <span className="chip c-neutral" role="status" aria-live="polite">
      <span style={{ width: 7, height: 7, borderRadius: '50%', background: color, display: 'inline-block' }} aria-hidden="true" />
      {LABEL[state]}
    </span>
  );
}

export type Pending = 'sending' | 'accepted' | 'rejected';

/**
 * Optimistic vs confirmed (brief §5): "Sending…" dimmed → Accepted (lime flash) or
 * Rejected (shake + the server's reason). The client never decides validity itself.
 */
export function PendingValue({ state, children, reason }: { state: Pending; children: ReactNode; reason?: string }) {
  return (
    <motion.span
      animate={state === 'rejected' ? shake : state === 'accepted' ? { backgroundColor: ['rgba(216,255,66,.35)', 'rgba(216,255,66,0)'] } : {}}
      transition={{ duration: 0.5 }}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 8, opacity: state === 'sending' ? 0.55 : 1, borderRadius: 4, padding: '2px 4px' }}
    >
      {children}
      {state === 'sending' && <span className="chip c-neutral">Sending…</span>}
      {state === 'accepted' && <span className="chip c-live">Accepted</span>}
      {state === 'rejected' && <span className="chip c-err">{reason ?? 'Rejected'}</span>}
    </motion.span>
  );
}
