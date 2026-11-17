import Link from 'next/link';
import clsx from 'clsx';
import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ReactNode } from 'react';

export type ButtonVariant = 'volt' | 'gold' | 'out' | 'ghost' | 'danger' | 'ok';
export type ButtonSize = 'sm' | 'md' | 'lg';

interface Common {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Shows a spinner and blocks clicks while a server round-trip is pending. */
  loading?: boolean;
  children: ReactNode;
  className?: string;
}

type AsButton = Common & ButtonHTMLAttributes<HTMLButtonElement> & { href?: undefined };
type AsLink = Common & AnchorHTMLAttributes<HTMLAnchorElement> & { href: string };

export function buttonClass(variant: ButtonVariant = 'out', size: ButtonSize = 'md', extra?: string) {
  return clsx('btn', `btn-${variant}`, size === 'sm' && 'btn-sm', size === 'lg' && 'btn-lg', extra);
}

function Spinner() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true" style={{ animation: 'ngbcl-spin .8s linear infinite' }}>
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeOpacity=".25" strokeWidth="3" />
      <path d="M21 12a9 9 0 0 0-9-9" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
      <style>{'@keyframes ngbcl-spin{to{transform:rotate(360deg)}}'}</style>
    </svg>
  );
}

/** DS button (.btn + .btn-volt/.btn-gold/.btn-out/.btn-ghost/.btn-danger/.btn-ok). */
export function Button(props: AsButton | AsLink) {
  const { variant = 'out', size = 'md', loading, className, children, ...rest } = props;
  const cls = buttonClass(variant, size, className);
  if (typeof props.href === 'string') {
    const { href, ...a } = rest as AnchorHTMLAttributes<HTMLAnchorElement> & { href: string };
    return (
      <Link href={href} className={cls} {...a}>
        {children}
      </Link>
    );
  }
  const b = rest as ButtonHTMLAttributes<HTMLButtonElement>;
  return (
    <button
      type={b.type ?? 'button'}
      {...b}
      className={cls}
      disabled={b.disabled || loading}
      aria-busy={loading || undefined}
    >
      {loading && <Spinner />}
      {children}
    </button>
  );
}
