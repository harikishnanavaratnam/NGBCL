import type { Metadata } from 'next';
import { ScorerGate, ScorerHome } from '@/features/scoring/ScorerHome';

export const metadata: Metadata = { title: 'My matches', robots: { index: false } };

/** Scorer app (tablet-first): assigned matches → live scoring. */
export default function ScorerPage() {
  return (
    <ScorerGate>
      <ScorerHome />
    </ScorerGate>
  );
}
