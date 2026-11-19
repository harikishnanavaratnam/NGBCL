import Link from 'next/link';
import { PublicLive } from '@/features/public/PublicLive';
import { PageBand } from '@/features/public/Sections';

export const metadata = {
  title: 'Live scores',
  description: 'NGBCL 1.0 live scores: every match on court, point by point.',
};

export default function LivePage() {
  return (
    <>
      <PageBand
        crumb={
          <>
            <Link href="/">Home</Link> / Live
          </>
        }
        eyebrow="Live scores"
        title="Live"
      >
        <p style={{ margin: 0, maxWidth: 640 }}>Scores update point by point. Results show as provisional until an official confirms them.</p>
      </PageBand>
      <PublicLive />
    </>
  );
}
