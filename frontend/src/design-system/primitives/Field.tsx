import clsx from 'clsx';
import { useId, type ReactNode } from 'react';

interface FieldProps {
  label: ReactNode;
  /** Renders "(optional)" next to the label (brief: labels above, helper below). */
  optional?: boolean;
  required?: boolean;
  help?: ReactNode;
  error?: string;
  success?: string;
  className?: string;
  children: (ids: { id: string; describedBy?: string; invalid: boolean }) => ReactNode;
}

/** Label + control + helper / error message, wired with aria-describedby. */
export function Field({ label, optional, required, help, error, success, className, children }: FieldProps) {
  const id = useId();
  const helpId = help ? `${id}-help` : undefined;
  const errId = error ? `${id}-err` : undefined;
  const describedBy = [errId, helpId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={clsx('field', error && 'err', className)}>
      <label htmlFor={id}>
        {label}
        {required && (
          <span className="req" aria-hidden="true">
            {' '}
            *
          </span>
        )}
        {optional && <span className="opt"> (optional)</span>}
      </label>
      {children({ id, describedBy, invalid: Boolean(error) })}
      {error && (
        <span className="errmsg" id={errId} role="alert">
          <ErrIcon /> {error}
        </span>
      )}
      {!error && success && (
        <span className="okmsg">
          <OkIcon /> {success}
        </span>
      )}
      {help && (
        <span className="help" id={helpId}>
          {help}
        </span>
      )}
    </div>
  );
}

function ErrIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
      <circle cx="12" cy="12" r="10" />
      <path d="M12 7v6M12 17h.01" strokeLinecap="round" />
    </svg>
  );
}
function OkIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true">
      <path d="M5 12.5l4.5 4.5L19 7.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
