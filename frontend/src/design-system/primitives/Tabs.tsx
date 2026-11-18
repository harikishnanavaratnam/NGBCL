'use client';

import clsx from 'clsx';
import Link from 'next/link';
import type { ReactNode } from 'react';

export interface TabItem<V extends string> {
  value: V;
  label: ReactNode;
  href?: string;
  count?: number;
}

/** Underlined tabs (.ds-tabs). Pass href for route tabs, onChange for local state. */
export function Tabs<V extends string>({
  items,
  value,
  onChange,
  label,
}: {
  items: TabItem<V>[];
  value: V;
  onChange?: (v: V) => void;
  label: string;
}) {
  return (
    <div className="ds-tabs" role="tablist" aria-label={label}>
      {items.map((t) => {
        const on = t.value === value;
        const content = (
          <>
            {t.label}
            {typeof t.count === 'number' && (
              <span className="chip c-neutral" style={{ marginLeft: 8, padding: '3px 6px' }}>
                {t.count}
              </span>
            )}
          </>
        );
        return t.href ? (
          <Link key={t.value} href={t.href} role="tab" aria-selected={on} className={clsx(on && 'on')}>
            {content}
          </Link>
        ) : (
          <button key={t.value} type="button" role="tab" aria-selected={on} className={clsx(on && 'on')} onClick={() => onChange?.(t.value)}>
            {content}
          </button>
        );
      })}
    </div>
  );
}

/** Segmented control (.seg) — e.g. Queue / Re-auction, List / Board / Bracket. */
export function Segmented<V extends string>({
  options,
  value,
  onChange,
  label,
}: {
  options: { value: V; label: ReactNode }[];
  value: V;
  onChange: (v: V) => void;
  label: string;
}) {
  return (
    <div className="seg" role="radiogroup" aria-label={label} style={{ alignSelf: 'flex-start', maxWidth: '100%', overflowX: 'auto' }}>
      {options.map((o) => (
        <button key={o.value} type="button" role="radio" aria-checked={o.value === value} className={clsx(o.value === value && 'on')} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}
