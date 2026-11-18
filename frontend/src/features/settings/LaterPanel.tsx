import { EmptyState } from '@/design-system/data/States';

export function LaterPanel({ title, milestone, note }: { title: string; milestone: string; note: string }) {
  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
      <h2 className="t-h2">{title}</h2>
      <EmptyState title={`Arrives in ${milestone}`} description={note} />
    </div>
  );
}
