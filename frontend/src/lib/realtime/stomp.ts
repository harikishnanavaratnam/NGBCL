'use client';

import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { API_URL } from '@/lib/api/client';

export type ConnectionState = 'connecting' | 'live' | 'reconnecting' | 'offline';

type Listener = (state: ConnectionState) => void;

/**
 * One shared STOMP connection per tab. The server only *broadcasts* over WebSocket;
 * commands (bids, score points) go over REST so they get auth, validation and idempotency.
 */
class RealtimeHub {
  private client: Client | null = null;
  private state: ConnectionState = 'offline';
  private listeners = new Set<Listener>();
  private refCount = 0;
  private everConnected = false;
  private tokenGetter: (() => string | null) | null = null;

  setTokenGetter(fn: (() => string | null) | null) {
    this.tokenGetter = fn;
  }

  private setState(s: ConnectionState) {
    this.state = s;
    this.listeners.forEach((l) => l(s));
  }

  onState(l: Listener) {
    this.listeners.add(l);
    l(this.state);
    return () => {
      this.listeners.delete(l);
    };
  }

  private ensure() {
    if (this.client) return this.client;
    const wsUrl = API_URL.replace(/^http/, 'ws') + '/ws';
    this.client = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 2000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: async (c) => {
        const token = this.tokenGetter?.();
        c.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
        this.setState(this.everConnected ? 'reconnecting' : 'connecting');
      },
      onConnect: () => {
        this.everConnected = true;
        this.setState('live');
      },
      onWebSocketClose: () => this.setState(this.everConnected ? 'reconnecting' : 'offline'),
      onStompError: () => this.setState('reconnecting'),
    });
    this.client.activate();
    return this.client;
  }

  /** Subscribe; resubscribes automatically after reconnects. Returns an unsubscribe fn. */
  subscribe(destination: string, onMessage: (body: unknown, raw: IMessage) => void) {
    const client = this.ensure();
    this.refCount++;
    let sub: StompSubscription | null = null;
    const attach = () => {
      sub = client.subscribe(destination, (msg) => {
        let body: unknown = msg.body;
        try {
          body = JSON.parse(msg.body);
        } catch {
          /* plain text */
        }
        onMessage(body, msg);
      });
    };
    if (client.connected) attach();
    const off = this.onState((s) => {
      if (s === 'live') attach();
    });
    return () => {
      off();
      sub?.unsubscribe();
      if (--this.refCount === 0) {
        void this.client?.deactivate();
        this.client = null;
        this.everConnected = false;
        this.setState('offline');
      }
    };
  }
}

export const realtime = new RealtimeHub();
