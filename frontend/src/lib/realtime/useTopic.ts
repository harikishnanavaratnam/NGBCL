'use client';

import { useEffect, useRef, useState } from 'react';
import { realtime, type ConnectionState } from './stomp';

/** Envelope every broadcast carries so clients can detect missed messages. */
export interface Versioned<T> {
  stateVersion: number;
  type: string;
  payload: T;
}

/**
 * Subscribe to a topic. If a message arrives with a version gap, `onGap` is called so the
 * caller can refetch the REST snapshot (server is the source of truth — SRS §8.3).
 */
export function useTopic<T>(
  destination: string | null,
  onMessage: (msg: Versioned<T>) => void,
  onGap?: () => void,
) {
  const last = useRef<number | null>(null);
  const handler = useRef(onMessage);
  const gap = useRef(onGap);
  useEffect(() => {
    handler.current = onMessage;
    gap.current = onGap;
  });

  useEffect(() => {
    if (!destination) return;
    last.current = null;
    return realtime.subscribe(destination, (body) => {
      const msg = body as Versioned<T>;
      if (typeof msg?.stateVersion === 'number') {
        if (last.current !== null && msg.stateVersion > last.current + 1) gap.current?.();
        if (last.current !== null && msg.stateVersion <= last.current) return; // stale / duplicate
        last.current = msg.stateVersion;
      }
      handler.current(msg);
    });
  }, [destination]);
}

export function useConnectionState(): ConnectionState {
  const [state, setState] = useState<ConnectionState>('offline');
  useEffect(() => realtime.onState(setState), []);
  return state;
}
