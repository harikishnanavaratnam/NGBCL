'use client';

import Link from 'next/link';
import { EmptyState } from '@/design-system/data/States';
import { ConnectionPill } from '@/design-system/live/Live';
import { useConnectionState } from '@/lib/realtime/useTopic';

/** Auctioneer console shell: no sidebar, session controls + Exit console (brief §6). */
export default function AuctionConsolePage() {
  const conn = useConnectionState();
  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '12px 24px', borderBottom: '1px solid var(--line)', background: 'var(--bg-2)' }}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/brand/ngbcl-wordmark-on-dark.svg" alt="NGBCL" style={{ height: 32, width: 'auto' }} />
        <span className="chip c-neutral">Auction not started</span>
        <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 16 }}>
          <ConnectionPill state={conn} />
          <Link href="/admin" className="link">
            Exit console
          </Link>
        </span>
      </header>
      <main id="main" style={{ flex: 1, padding: 24 }}>
        <EmptyState title="The console goes live in Milestone 4" description="Lot queue, bid entry, SOLD / UNSOLD / WITHDRAWN and the team budget rail will appear here." />
      </main>
    </div>
  );
}
