import type { Metadata } from 'next';
import { Suspense } from 'react';
import { Skeleton } from '@/design-system/data/States';
import { ScorerGate } from '@/features/scoring/ScorerHome';
import { ScoringScreen } from '@/features/scoring/ScoringScreen';

export const metadata: Metadata = { title: 'Scoring', robots: { index: false } };

export default function ScoreMatchPage({ params }: PageProps<'/score/[matchId]'>) {
  return (
    <Suspense fallback={<Skeleton height={480} />}>
      <MatchFromParams params={params} />
    </Suspense>
  );
}

async function MatchFromParams({ params }: Pick<PageProps<'/score/[matchId]'>, 'params'>) {
  const { matchId } = await params;
  return (
    <ScorerGate>
      <ScoringScreen matchId={matchId} />
    </ScorerGate>
  );
}
