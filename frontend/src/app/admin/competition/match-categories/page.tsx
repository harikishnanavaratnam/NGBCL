import { MatchCategoriesPanel } from '@/features/competition/MatchCategoriesPanel';

export default function Page() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <nav aria-label="Breadcrumb" className="t-caption">
          Competition / Match categories
        </nav>
        <h1 className="t-h1">Match categories</h1>
      </div>
      <div className="card">
        <MatchCategoriesPanel />
      </div>
    </div>
  );
}
