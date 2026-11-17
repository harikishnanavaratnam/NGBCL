'use client';

import { motion } from 'motion/react';
import { ConnectionPill } from '@/design-system/live/Live';
import { useConnectionState } from '@/lib/realtime/useTopic';
import { ScaledCanvas } from './ScaledCanvas';

/** Big-screen intro state (export AucDisp-1-Intro). Live lot states arrive in M4. */
export function AuctionDisplayIdle() {
  const conn = useConnectionState();
  return (
    <ScaledCanvas>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/brand/hero-poster.jpg" alt="" aria-hidden="true" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', opacity: 0.35 }} />
      <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(90deg, rgba(16,19,24,.96) 0%, rgba(16,19,24,.7) 55%, rgba(16,19,24,.3) 100%)' }} />
      <div style={{ position: 'absolute', top: 48, right: 64 }}>
        <ConnectionPill state={conn} />
      </div>
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
        style={{ position: 'absolute', left: 128, top: 220, display: 'flex', flexDirection: 'column', gap: 32 }}
      >
        <span className="eyebrow" style={{ fontSize: 22, color: 'var(--gold-2)' }}>
          Mighty Sharks Battalion presents
        </span>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/brand/ngbcl-logo-on-dark.svg" alt="NGBCL" style={{ height: 260, width: 'auto', alignSelf: 'flex-start' }} />
        <h1 className="disp" style={{ fontSize: 132 }}>
          Player <span style={{ color: 'var(--lime)' }}>auction</span>
        </h1>
        <p className="disp" style={{ fontSize: 40, color: 'var(--text-2)', fontWeight: 600 }}>
          Starting soon · Rise. Rally. Rule the Court.
        </p>
      </motion.div>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/brand/mighty-sharks.webp" alt="Mighty Sharks Battalion" style={{ position: 'absolute', right: 64, bottom: 56, width: 120, height: 120, borderRadius: 12 }} />
    </ScaledCanvas>
  );
}
