import type { CSSProperties, ReactNode } from 'react';
import { Button } from '../primitives/Button';

/** Court-line illustration used by every empty state (brief §2 signature devices). */
export function CourtArt({ width = 160 }: { width?: number }) {
  return (
    <svg width={width} height={(width * 61) / 134} viewBox="0 0 134 61" fill="none" aria-hidden="true" style={{ opacity: 0.55 }}>
      <g stroke="var(--line-3)" strokeWidth="1">
        <rect x="0.5" y="0.5" width="133" height="60" />
        <line x1="0.5" y1="5" x2="133.5" y2="5" />
        <line x1="0.5" y1="56" x2="133.5" y2="56" />
        <line x1="67" y1="0.5" x2="67" y2="60.5" strokeDasharray="2 2" />
        <line x1="47" y1="5" x2="47" y2="56" />
        <line x1="87" y1="5" x2="87" y2="56" />
        <line x1="8" y1="30.5" x2="47" y2="30.5" />
        <line x1="87" y1="30.5" x2="126" y2="30.5" />
        <line x1="8" y1="0.5" x2="8" y2="60.5" />
        <line x1="126" y1="0.5" x2="126" y2="60.5" />
      </g>
    </svg>
  );
}

const box: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  textAlign: 'center',
  gap: 14,
  padding: '48px 24px',
  border: '1px dashed var(--line-2)',
  borderRadius: 'var(--r-m)',
};

/** One sentence + one action (brief §5 data display). */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: ReactNode;
  action?: { label: string; href?: string; onClick?: () => void };
}) {
  return (
    <div style={box} role="status">
      <CourtArt />
      <h3 className="t-h3">{title}</h3>
      {description && <p className="t-body-s muted" style={{ maxWidth: 420 }}>{description}</p>}
      {action &&
        (action.href ? (
          <Button href={action.href} variant="volt" size="sm">
            {action.label}
          </Button>
        ) : (
          <Button onClick={action.onClick} variant="volt" size="sm">
            {action.label}
          </Button>
        ))}
    </div>
  );
}

export function ErrorState({ title = 'Couldn’t load this', message, onRetry }: { title?: string; message?: ReactNode; onRetry?: () => void }) {
  return (
    <div style={{ ...box, borderColor: 'rgba(255,113,120,.4)', background: 'var(--err-tint)' }} role="alert">
      <h3 className="t-h3" style={{ color: 'var(--err)' }}>
        {title}
      </h3>
      {message && <p className="t-body-s" style={{ maxWidth: 460 }}>{message}</p>}
      {onRetry && (
        <Button onClick={onRetry} variant="out" size="sm">
          Try again
        </Button>
      )}
    </div>
  );
}

/** Shimmer placeholder block. */
export function Skeleton({ width = '100%', height = 16, radius = 4, style }: { width?: number | string; height?: number | string; radius?: number; style?: CSSProperties }) {
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'block',
        width,
        height,
        borderRadius: radius,
        background: 'linear-gradient(90deg, var(--surface) 0%, var(--surface-2) 50%, var(--surface) 100%)',
        backgroundSize: '200% 100%',
        animation: 'ngbcl-shimmer 1.4s ease-in-out infinite',
        ...style,
      }}
    >
      <style>{'@keyframes ngbcl-shimmer{0%{background-position:200% 0}100%{background-position:-200% 0}}'}</style>
    </span>
  );
}

export function SkeletonRows({ rows = 6, cols = 5 }: { rows?: number; cols?: number }) {
  return (
    <div role="status" aria-label="Loading" style={{ display: 'flex', flexDirection: 'column', gap: 14, padding: 12 }}>
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} style={{ display: 'grid', gridTemplateColumns: `repeat(${cols}, 1fr)`, gap: 16 }}>
          {Array.from({ length: cols }, (_, c) => (
            <Skeleton key={c} height={14} width={c === 0 ? '70%' : '50%'} />
          ))}
        </div>
      ))}
    </div>
  );
}

/** Stat block: large number + label + optional delta. Use in context groups, never a KPI wall. */
export function StatBlock({ label, value, sub }: { label: string; value: ReactNode; sub?: ReactNode }) {
  return (
    <div className="ds-stat">
      <span className="eyebrow">{label}</span>
      <span className="v">{value}</span>
      {sub && <span className="t-caption">{sub}</span>}
    </div>
  );
}
