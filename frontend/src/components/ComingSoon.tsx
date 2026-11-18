import { EmptyState } from '@/design-system/data/States';
import { Eyebrow } from '@/design-system/primitives/Text';

/** Route placeholder so navigation never 404s while a milestone is in progress. */
export function ComingSoon({ eyebrow, title, milestone, description }: { eyebrow: string; title: string; milestone: string; description?: string }) {
  return (
    <section className="wrap" style={{ padding: '64px 32px', display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <Eyebrow>{eyebrow}</Eyebrow>
        <h1 className="disp t-display-l">{title}</h1>
      </div>
      <EmptyState title={`Arrives in ${milestone}`} description={description ?? 'This page is part of an upcoming milestone.'} action={{ label: 'Back to home', href: '/' }} />
    </section>
  );
}
