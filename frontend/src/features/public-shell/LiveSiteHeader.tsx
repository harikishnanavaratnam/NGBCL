'use client';

import { useQueryClient } from '@tanstack/react-query';
import { liveKey, usePublicLive } from '@/lib/api/scoring';
import { useTopic } from '@/lib/realtime/useTopic';
import { SiteHeader } from './SiteHeader';

/** Site header with the live strip filled from /api/public/live (hidden when nothing is on court). */
export function LiveSiteHeader({ facebookUrl }: { facebookUrl?: string | null }) {
  const qc = useQueryClient();
  const { data } = usePublicLive();
  useTopic('/topic/public/live', () => void qc.invalidateQueries({ queryKey: liveKey }));
  const live = data?.live ?? [];
  const first = live[0];
  const summary = first
    ? {
        label:
          live.length === 1
            ? `${first.teamA.name} v ${first.teamB.name} · ${first.categoryName} · ${first.scoreA}–${first.scoreB}`
            : `${live.length} matches on court · ${first.teamA.name} v ${first.teamB.name} and more`,
        href: '/live',
      }
    : null;
  return <SiteHeader live={summary} facebookUrl={facebookUrl} />;
}
