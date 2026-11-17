'use client';

import { animate, useReducedMotion } from 'motion/react';
import { useEffect, useRef } from 'react';
import { DURATION, EASE_OUT_EXPO } from './presets';

interface Props {
  value: number;
  format?: (n: number) => string;
  duration?: number;
  className?: string;
}

/** Scoreboard number that rolls to its new value (bid change: 320 ms ease-out-expo). */
export function CountUp({ value, format = (n) => String(Math.round(n)), duration = DURATION.bid, className }: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const prev = useRef(value);
  const reduce = useReducedMotion();

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (reduce || prev.current === value) {
      el.textContent = format(value);
      prev.current = value;
      return;
    }
    const controls = animate(prev.current, value, {
      duration,
      ease: EASE_OUT_EXPO,
      onUpdate: (v) => {
        el.textContent = format(v);
      },
    });
    prev.current = value;
    return () => controls.stop();
  }, [value, duration, format, reduce]);

  return (
    <span ref={ref} className={className} style={{ fontVariantNumeric: 'tabular-nums' }}>
      {format(value)}
    </span>
  );
}
