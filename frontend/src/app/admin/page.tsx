import Link from 'next/link';
import { EmptyState } from '@/design-system/data/States';
import { StatusChip } from '@/design-system/primitives/StatusChip';

const STAGES = ['Setup', 'Registration', 'Review', 'Auction', 'Fixtures', 'Matches', 'Completed'];

/** Overview — lifecycle rail (export AdminDash). Live panels arrive with their milestones. */
export default function AdminOverview() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <span className="eyebrow">Overview</span>
          <h1 className="t-h1">NGBCL 1.0 — tournament lifecycle</h1>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <StatusChip status="DRAFT">Status: Draft</StatusChip>
          <Link href="/" className="btn btn-out">
            View public site
          </Link>
        </div>
      </div>
      <section className="card" style={{ padding: 24 }} aria-label="Lifecycle">
        <ol style={{ display: 'grid', gridTemplateColumns: `repeat(${STAGES.length}, minmax(0,1fr))`, listStyle: 'none', margin: 0, padding: 0, gap: 8, overflowX: 'auto' }}>
          {STAGES.map((s, i) => (
            <li key={s} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, minWidth: 80 }}>
              <span
                className="num"
                style={{ width: 28, height: 28, borderRadius: '50%', border: `1px solid ${i === 0 ? 'var(--lime)' : 'var(--line-3)'}`, display: 'grid', placeItems: 'center', fontSize: 14, color: i === 0 ? 'var(--lime)' : 'var(--text-2)' }}
              >
                {i + 1}
              </span>
              <span style={{ fontSize: 13, fontWeight: i === 0 ? 600 : 500, color: i === 0 ? 'var(--text)' : 'var(--text-2)' }}>{s}</span>
            </li>
          ))}
        </ol>
      </section>
      <EmptyState
        title="Start by setting up the tournament"
        description="Add the dates and venue, set the registration windows, choose skill categories and upload branding."
        action={{ label: 'Open settings', href: '/admin/system/settings' }}
      />
    </div>
  );
}
