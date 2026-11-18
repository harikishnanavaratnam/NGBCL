'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';

/**
 * Fixed 1920×1080 canvas scaled to fit any projector / TV, letterboxed (brief §6).
 * Layout inside is authored in canvas pixels, so it looks identical everywhere.
 */
export function ScaledCanvas({ children, width = 1920, height = 1080 }: { children: ReactNode; width?: number; height?: number }) {
  const outer = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const el = outer.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => {
      const { width: w, height: h } = e.contentRect;
      setScale(Math.min(w / width, h / height));
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, [width, height]);
  return (
    <div ref={outer} style={{ position: 'fixed', inset: 0, background: '#05080F', display: 'grid', placeItems: 'center', overflow: 'hidden' }}>
      <div style={{ width: width * scale, height: height * scale, position: 'relative', overflow: 'hidden' }}>
        <div style={{ width, height, transform: `scale(${scale})`, transformOrigin: 'top left', position: 'absolute', top: 0, left: 0, background: 'var(--bg)', overflow: 'hidden' }}>
          {children}
        </div>
      </div>
    </div>
  );
}
