'use client';

import clsx from 'clsx';
import type { ReactNode } from 'react';
import styles from './RadioCard.module.css';

export interface RadioOption<V extends string> {
  value: V;
  label: ReactNode;
  description?: ReactNode;
  disabled?: boolean;
}

interface Props<V extends string> {
  name: string;
  value: V | null;
  onChange: (v: V) => void;
  options: RadioOption<V>[];
  legend: ReactNode;
  columns?: number;
}

/** Radio cards — used for skill category selection (brief §5). */
export function RadioCards<V extends string>({ name, value, onChange, options, legend, columns = 3 }: Props<V>) {
  return (
    <fieldset className={styles.set}>
      <legend className="lbl">{legend}</legend>
      <div className={styles.grid} style={{ gridTemplateColumns: `repeat(auto-fit, minmax(${columns > 2 ? 180 : 240}px, 1fr))` }}>
        {options.map((o) => (
          <label key={o.value} className={clsx(styles.card, value === o.value && styles.on, o.disabled && styles.disabled)}>
            <input
              type="radio"
              name={name}
              value={o.value}
              checked={value === o.value}
              disabled={o.disabled}
              onChange={() => onChange(o.value)}
              className="sr-only"
            />
            <span className={styles.radio} aria-hidden="true" />
            <span className={styles.text}>
              <strong>{o.label}</strong>
              {o.description && <span className="help">{o.description}</span>}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
