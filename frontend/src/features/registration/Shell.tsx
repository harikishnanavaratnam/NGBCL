'use client';

import clsx from 'clsx';
import { motion } from 'motion/react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { ConfirmDialog } from '@/design-system/primitives/Overlay';
import s from './Registration.module.css';

/** Warns before the tab closes or the user leaves with unsent answers (brief: "leave guard"). */
export function useLeaveGuard(active: boolean) {
  useEffect(() => {
    if (!active) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [active]);
}

export function RegShell({ dirty = false, children }: { dirty?: boolean; children: ReactNode }) {
  const router = useRouter();
  const [confirm, setConfirm] = useState(false);
  useLeaveGuard(dirty);
  return (
    <div className={s.root}>
      <header className={s.header}>
        <div className={clsx(s.wrap, s.headerInner)}>
          <Link href="/" className={s.brand} aria-label="NGBCL 1.0 home" onClick={(e) => dirty && (e.preventDefault(), setConfirm(true))}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/brand/ngbcl-logo-on-dark.svg" alt="NGBCL — Next Gen Badminton Champions League" width={100} height={36} />
            <span className="chip c-gold" style={{ padding: '4px 6px' }}>
              1.0
            </span>
          </Link>
          <button type="button" className={s.exit} onClick={() => (dirty ? setConfirm(true) : router.push('/'))}>
            Exit
          </button>
        </div>
      </header>
      {children}
      <footer className={s.footer}>
        <div className={clsx(s.wrap, s.footerInner)}>
          <span className="muted">NGBCL 1.0 · Mighty Sharks Battalion, Jaffna</span>
          <Link href="/privacy" style={{ fontSize: 13, color: 'var(--text)' }}>
            Privacy notice
          </Link>
        </div>
      </footer>
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        onConfirm={() => router.push('/')}
        title="Leave registration?"
        consequences="Your answers and photo haven't been sent. If you leave now you'll need to start again."
        confirmLabel="Leave without sending"
        tone="danger"
      />
    </div>
  );
}

export function RegBand({
  eyebrow,
  title,
  children,
  variant = 'intro',
  shuttle = true,
  lead,
}: {
  /** Rendered above the title (status chip on state screens). */
  lead?: ReactNode;
  eyebrow?: ReactNode;
  title: ReactNode;
  children?: ReactNode;
  variant?: 'intro' | 'state' | 'center';
  shuttle?: boolean;
}) {
  return (
    <section className={s.band}>
      {shuttle && variant === 'intro' && (
        // eslint-disable-next-line @next/next/no-img-element
        <img className={s.shuttle} src="/brand/shuttle.webp" alt="" aria-hidden="true" />
      )}
      <motion.div
        className={clsx(s.wrap, s.bandInner, variant === 'state' && s.bandTall, variant === 'center' && s.bandCenter)}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
      >
        {lead}
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h1 className={clsx('disp', s.title, variant !== 'intro' && s.titleXl)}>{title}</h1>
        {children}
      </motion.div>
    </section>
  );
}

export interface StepDef {
  label: string;
}

/** Vertical stepper (desktop) — completed steps can be revisited, later ones can't be skipped. */
export function VStepper({
  steps,
  current,
  maxReached,
  onGo,
}: {
  steps: StepDef[];
  current: number;
  maxReached: number;
  onGo: (i: number) => void;
}) {
  return (
    <nav className={clsx('card', s.vstep)} aria-label="Registration steps">
      {steps.map((st, i) => {
        const done = i < current || (i <= maxReached && i !== current);
        const cur = i === current;
        return (
          <button
            key={st.label}
            type="button"
            className={s.stepBtn}
            onClick={() => onGo(i)}
            disabled={i > maxReached}
            aria-current={cur ? 'step' : undefined}
          >
            <span className={clsx(s.stepNum, done && s.stepDone, cur && s.stepCur)}>
              {done && !cur ? (
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="3"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M5 12l5 5 9-10" />
                </svg>
              ) : (
                i + 1
              )}
            </span>
            <span className={s.stepText}>
              <span className={s.stepLabel}>{st.label}</span>
              <span className={s.stepStatus}>{stepStatus(i, current, maxReached)}</span>
            </span>
          </button>
        );
      })}
    </nav>
  );
}

export function stepStatus(i: number, current: number, maxReached: number) {
  if (i === current) return 'In progress';
  if (i < current || i <= maxReached) return 'Complete';
  return 'Not started';
}

/** Compact stepper with a gold progress arc (tablet / mobile). */
export function HStepper({ steps, current }: { steps: StepDef[]; current: number }) {
  const n = current + 1;
  const dash = Math.round((n / steps.length) * 113);
  return (
    <div className={clsx('card', s.hstep)}>
      <svg width="44" height="44" viewBox="0 0 44 44" aria-hidden="true">
        <circle cx="22" cy="22" r="18" fill="none" stroke="rgba(245,247,250,.09)" strokeWidth="4" />
        <motion.circle
          cx="22"
          cy="22"
          r="18"
          fill="none"
          stroke="var(--gold)"
          strokeWidth="4"
          strokeLinecap="round"
          transform="rotate(-90 22 22)"
          initial={false}
          animate={{ strokeDasharray: `${dash} 113` }}
          transition={{ duration: 0.32 }}
        />
      </svg>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <span className="eyebrow">
          Step {n} of {steps.length}
        </span>
        <strong style={{ fontSize: 16 }}>{steps[current]?.label}</strong>
      </div>
      <span className="muted" style={{ marginLeft: 'auto', fontSize: 12 }}>
        Next: {steps[current + 1]?.label ?? 'Submit'}
      </span>
    </div>
  );
}

export function ErrorSummary({ items }: { items: { id: string; label: string }[] }) {
  if (items.length === 0) return null;
  return (
    <motion.div role="alert" className={clsx(s.alert, s.alertList)} initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }}>
      <strong style={{ color: 'var(--err)' }}>
        {items.length === 1 ? '1 thing needs your attention' : `${items.length} things need your attention`}
      </strong>
      {items.map((i) => (
        <a key={i.id + i.label} href={`#${i.id}`}>
          {i.label}
        </a>
      ))}
    </motion.div>
  );
}

export function ReviewBlock({ title, onEdit, rows }: { title: string; onEdit: () => void; rows: { k: string; v: ReactNode }[] }) {
  return (
    <div className={s.reviewBlock}>
      <div className={s.reviewHead}>
        <strong>{title}</strong>
        <button type="button" className={s.linkBtn} onClick={onEdit}>
          Edit
        </button>
      </div>
      <div className={s.row2} style={{ gap: '8px 20px', fontSize: 14 }}>
        {rows.map((r) => (
          <div key={r.k} className={s.kv}>
            <span className="eyebrow">{r.k}</span>
            <span>{r.v}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function Declaration({
  id,
  checked,
  onChange,
  error,
  children,
}: {
  id?: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label id={id} className={clsx(s.decl, error && s.declErr)}>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} aria-invalid={Boolean(error)} />
      <span>
        {children} <span className="req">*</span>
        {error && (
          <>
            <br />
            <span className="errmsg" style={{ marginTop: 4 }}>
              {error}
            </span>
          </>
        )}
      </span>
    </label>
  );
}
