import type { Metadata } from 'next';
import { AuctionDisplayIdle } from '@/features/display/AuctionDisplayIdle';

export const metadata: Metadata = { title: 'Auction display', robots: { index: false } };

export default function AuctionDisplayPage() {
  return <AuctionDisplayIdle />;
}
