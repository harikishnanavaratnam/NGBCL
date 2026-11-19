'use client';

import { useQueryClient } from '@tanstack/react-query';
import { AUCTION_TOPIC, consoleKey, publicStateKey, type PublicAuctionState } from '@/lib/api/auction';
import { useTopic } from '@/lib/realtime/useTopic';

/**
 * Keeps auction screens in step with the server. Every change is broadcast with the public
 * state; staff screens also refetch their snapshot. A version gap (missed message) refetches
 * everything — the database is the source of truth (SRS §8.3).
 */
export function useAuctionLive({ staff, onEvent }: { staff?: boolean; onEvent?: (type: string, state: PublicAuctionState) => void } = {}) {
  const qc = useQueryClient();
  useTopic<PublicAuctionState>(
    AUCTION_TOPIC,
    (msg) => {
      const current = qc.getQueryData<PublicAuctionState>(publicStateKey);
      if (!current || msg.payload.stateVersion >= current.stateVersion) qc.setQueryData(publicStateKey, msg.payload);
      if (staff) void qc.invalidateQueries({ queryKey: consoleKey });
      onEvent?.(msg.type, msg.payload);
    },
    () => {
      void qc.invalidateQueries({ queryKey: publicStateKey });
      if (staff) void qc.invalidateQueries({ queryKey: consoleKey });
    },
  );
}
