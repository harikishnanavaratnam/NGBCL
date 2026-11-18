'use client';

import { useSyncExternalStore } from 'react';
import { Tbc } from '@/design-system/primitives/Text';

function parts(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  return { DAYS: Math.floor(s / 86400), HRS: Math.floor((s % 86400) / 3600), MIN: Math.floor((s % 3600) / 60), SEC: s % 60 };
}

/** Countdown to registration close. Shows "--" tiles + a TBC tag until the admin sets the date. */
export function Countdown({ target, label }: { target: string | null; label: string }) {
  // Ticks once a second on the client; null during SSR so markup hydrates cleanly.
  const now = useSyncExternalStore(
    (cb) => {
      const t = setInterval(cb, 1000);
      return () => clearInterval(t);
    },
    () => Math.floor(Date.now() / 1000) * 1000,
    () => null,
  );

  const p = target && now !== null ? parts(new Date(target).getTime() - now) : null;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <span className="eyebrow">
        {label} {!target && <Tbc>[CLOSING DATE TBC]</Tbc>}
      </span>
      <div style={{ display: 'flex', gap: 10 }} role="timer" aria-live="off">
        {(['DAYS', 'HRS', 'MIN', 'SEC'] as const).map((k) => (
          <div key={k} style={{ width: 72, padding: '10px 0', textAlign: 'center', border: '1px solid rgba(245,247,250,.2)', borderRadius: 6, background: 'rgba(12,14,18,.5)' }}>
            <div className="num" style={{ fontSize: 30, lineHeight: 1 }}>
              {p ? String(p[k]).padStart(2, '0') : '--'}
            </div>
            <div className="eyebrow" style={{ fontSize: 10 }}>
              {k}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
