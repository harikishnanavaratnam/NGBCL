import { ScoringSettingsPanel } from '@/features/scoring/ScoringSettingsPanel';

export default function Page() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <nav aria-label="Breadcrumb" className="t-caption">
          Competition / Scoring rules
        </nav>
        <h1 className="t-h1">Scoring rules</h1>
      </div>
      <div className="card">
        <ScoringSettingsPanel />
      </div>
    </div>
  );
}
