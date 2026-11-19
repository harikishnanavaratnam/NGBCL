import Link from 'next/link';
import { PublicAuction } from '@/features/public/PublicAuction';
import { PageBand } from '@/features/public/Sections';

export const metadata = {
  title: 'Player auction',
  description: 'Live player auction and results: every sale, every team’s squad.',
};

export default function AuctionPage() {
  return (
    <>
      <PageBand
        crumb={
          <>
            <Link href="/">Home</Link> / Auction
          </>
        }
        eyebrow="NGBCL 1.0"
        title="Player auction"
      >
        <p style={{ margin: 0, maxWidth: 640 }}>Teams build their squads at the live auction. This page updates as each player is sold.</p>
      </PageBand>
      <PublicAuction />
    </>
  );
}
