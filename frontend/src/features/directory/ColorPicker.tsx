'use client';

import { TEAM_COLORS } from '@/design-system/tokens';
import s from '../registrations/Registrations.module.css';

/** Team palette picker (10 slots). Slots used by other teams are disabled. */
export function ColorPicker({
  value,
  onChange,
  used,
  allowAuto,
}: {
  value: number | null;
  onChange: (v: number | null) => void;
  used: Set<number>;
  allowAuto?: boolean;
}) {
  return (
    <div className={s.swatches} role="radiogroup" aria-label="Team colour">
      {allowAuto && (
        <button
          type="button"
          role="radio"
          aria-checked={value === null}
          className="chip c-neutral"
          style={{ cursor: 'pointer', border: value === null ? '1px solid var(--text)' : undefined }}
          onClick={() => onChange(null)}
        >
          Next free
        </button>
      )}
      {TEAM_COLORS.map((c) => (
        <button
          key={c.slot}
          type="button"
          role="radio"
          aria-checked={value === c.slot}
          aria-label={`${c.name}${used.has(c.slot) && value !== c.slot ? ' (taken)' : ''}`}
          title={c.name}
          className={s.swatch}
          style={{ background: c.hex }}
          disabled={used.has(c.slot) && value !== c.slot}
          onClick={() => onChange(c.slot)}
        />
      ))}
    </div>
  );
}
