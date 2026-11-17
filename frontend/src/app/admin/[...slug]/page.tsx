import { Suspense } from 'react';
import { EmptyState, Skeleton } from '@/design-system/data/States';

/** Catch-all for admin sections not built yet, so the role-filtered sidebar never 404s. */
export default function AdminPlaceholder({ params }: PageProps<'/admin/[...slug]'>) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      {/* params are request-time data → stream them inside Suspense so the shell stays instant */}
      <Suspense fallback={<Skeleton height={44} width={280} />}>
        <SectionTitle params={params} />
      </Suspense>
      <EmptyState title="Coming in a later milestone" description="This section is scaffolded in the navigation; its screens are built in the milestone that owns it." />
    </div>
  );
}

async function SectionTitle({ params }: { params: PageProps<'/admin/[...slug]'>['params'] }) {
  const { slug } = await params;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span className="eyebrow">{slug[0]}</span>
      <h1 className="t-h1" style={{ textTransform: 'capitalize' }}>
        {slug.map((s) => s.replace(/-/g, ' ')).join(' · ')}
      </h1>
    </div>
  );
}
