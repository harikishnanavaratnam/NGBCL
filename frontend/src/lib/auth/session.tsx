'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, registerTokenSource } from '@/lib/api/client';
import { ApiError } from '@/lib/api/errors';
import type { LoginRequest, Me, TokenResponse } from '@/lib/api/types';
import { hasAny, type Permission } from './permissions';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: Status;
  user: Me | null;
  login: (req: LoginRequest) => Promise<Me>;
  logout: () => Promise<void>;
  can: (...required: Permission[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Access token lives in memory only. The refresh token is an httpOnly cookie set by the API,
 * so a page reload silently restores the session via POST /auth/refresh.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<Me | null>(null);
  const tokenRef = useRef<string | null>(null);
  const refreshing = useRef<Promise<string | null> | null>(null);

  const apply = useCallback((res: TokenResponse | null) => {
    tokenRef.current = res?.accessToken ?? null;
    setUser(res?.user ?? null);
    setStatus(res ? 'authenticated' : 'anonymous');
    return res?.accessToken ?? null;
  }, []);

  const refresh = useCallback(() => {
    // De-duplicate concurrent refreshes (several queries can 401 at once)
    const call = () => api<TokenResponse>('/auth/refresh', { method: 'POST', anonymous: true });
    refreshing.current ??= call()
      // Another tab rotated the token a moment ago; its new cookie is already set — retry once.
      .catch((e) => (e instanceof ApiError && e.code === 'REFRESH_RACE' ? new Promise((r) => setTimeout(r, 400)).then(call) : Promise.reject(e)))
      .then(apply)
      .catch(() => apply(null))
      .finally(() => {
        refreshing.current = null;
      });
    return refreshing.current;
  }, [apply]);

  useEffect(() => {
    registerTokenSource({ get: () => tokenRef.current, refresh });
    // Only try to restore a session when the API left its non-sensitive hint cookie.
    if (typeof document !== 'undefined' && document.cookie.includes('ngbcl_session=')) {
      void refresh();
    } else {
      void Promise.resolve().then(() => setStatus('anonymous'));
    }
    return () => registerTokenSource(null);
  }, [refresh]);

  const login = useCallback(
    async (req: LoginRequest) => {
      const res = await api<TokenResponse>('/auth/login', { method: 'POST', body: req, anonymous: true });
      apply(res);
      return res.user;
    },
    [apply],
  );

  const logout = useCallback(async () => {
    try {
      await api<void>('/auth/logout', { method: 'POST' });
    } finally {
      apply(null);
    }
  }, [apply]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, can: (...p) => hasAny(user?.permissions, p) }),
    [status, user, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

/** Where each role lands after sign-in (brief §6). */
export function homePathFor(user: Pick<Me, 'homeSurface'>) {
  switch (user.homeSurface) {
    case 'AUCTION_CONSOLE':
      return '/console/auction';
    case 'SCORER':
      return '/score';
    default:
      return '/admin';
  }
}
