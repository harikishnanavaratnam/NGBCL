import type { Metadata } from 'next';
import { AuctionConsoleGate } from '@/features/auction/console/AuctionConsole';

export const metadata: Metadata = { title: 'Auction console', robots: { index: false } };

/** Auctioneer console: own surface, no admin sidebar (brief §6). */
export default function AuctionConsolePage() {
  return <AuctionConsoleGate />;
}
