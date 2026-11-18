'use client';

import { motion } from 'motion/react';
import type { ReactNode } from 'react';
import { bidTransition } from './presets';

/**
 * Wraps a value that should flash lime when it changes (accepted bid, new point).
 * `flashKey` changes → the background pulses from lime tint to transparent.
 */
export function Flash({ flashKey, children, className }: { flashKey: string | number; children: ReactNode; className?: string }) {
  return (
    <motion.span
      key={flashKey}
      className={className}
      initial={{ backgroundColor: 'rgba(216,255,66,.35)' }}
      animate={{ backgroundColor: 'rgba(216,255,66,0)' }}
      transition={{ ...bidTransition, duration: 0.6 }}
      style={{ borderRadius: 4, display: 'inline-block' }}
    >
      {children}
    </motion.span>
  );
}
