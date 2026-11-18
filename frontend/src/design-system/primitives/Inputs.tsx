import clsx from 'clsx';
import { forwardRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, ...p },
  ref,
) {
  return <input ref={ref} className={clsx('inp', className)} {...p} />;
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, rows = 4, ...p },
  ref,
) {
  return <textarea ref={ref} rows={rows} className={clsx('inp', className)} {...p} />;
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { className, children, ...p },
  ref,
) {
  return (
    <select ref={ref} className={clsx('inp', className)} {...p}>
      {children}
    </select>
  );
});

/** Sri Lankan phone with fixed +94 prefix (brief §5 primitives). Value excludes the prefix. */
export const PhoneInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function PhoneInput(
  { className, style, ...p },
  ref,
) {
  return (
    <div style={{ display: 'flex', alignItems: 'stretch', ...style }} className={className}>
      <span
        className="inp"
        aria-hidden="true"
        style={{ width: 'auto', borderTopRightRadius: 0, borderBottomRightRadius: 0, borderRight: 0, color: 'var(--text-2)', background: 'var(--bg-2)' }}
      >
        +94
      </span>
      <input
        ref={ref}
        type="tel"
        inputMode="tel"
        autoComplete="tel-national"
        placeholder="77 123 4567"
        className="inp"
        style={{ borderTopLeftRadius: 0, borderBottomLeftRadius: 0 }}
        {...p}
      />
    </div>
  );
});

/** DS switch (.sw) with a visible label. */
export const Toggle = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label: ReactNode }>(
  function Toggle({ label, className, id, ...p }, ref) {
    return (
      <label className={className} htmlFor={id} style={{ display: 'inline-flex', alignItems: 'center', gap: 10, cursor: 'pointer', fontSize: 14 }}>
        <input ref={ref} id={id} type="checkbox" role="switch" className="sw" {...p} />
        {label}
      </label>
    );
  },
);

export const Checkbox = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label: ReactNode }>(
  function Checkbox({ label, className, ...p }, ref) {
    return (
      <label className={className} style={{ display: 'inline-flex', alignItems: 'flex-start', gap: 10, cursor: 'pointer', fontSize: 14 }}>
        <input ref={ref} type="checkbox" style={{ width: 18, height: 18, marginTop: 2 }} {...p} />
        <span>{label}</span>
      </label>
    );
  },
);
