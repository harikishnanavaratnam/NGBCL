import { EmptyState } from '@/design-system/data/States';

export const metadata = { title: 'My matches', robots: { index: false } };

/** Scorer console shell — tablet-first; bottom tab bar (My matches · Live · History) arrives in M6. */
export default function ScorerHome() {
  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', borderBottom: '1px solid var(--line)', background: 'var(--bg-2)' }}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/brand/ngbcl-mark-on-dark.svg" alt="" aria-hidden="true" style={{ height: 32, width: 32 }} />
        <strong>My matches</strong>
      </header>
      <main id="main" style={{ flex: 1, padding: 16 }}>
        <EmptyState title="No matches assigned yet" description="Matches assigned to you by the tournament admin will appear here (Milestone 6)." />
      </main>
      <nav aria-label="Scorer" style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', borderTop: '1px solid var(--line)', background: 'var(--bg-2)' }}>
        {['My matches', 'Live', 'History'].map((t, i) => (
          <span key={t} style={{ minHeight: 56, display: 'grid', placeItems: 'center', fontSize: 13, fontWeight: 600, color: i === 0 ? 'var(--text)' : 'var(--text-3)', boxShadow: i === 0 ? 'inset 0 2px 0 var(--lime)' : undefined }}>
            {t}
          </span>
        ))}
      </nav>
    </div>
  );
}
