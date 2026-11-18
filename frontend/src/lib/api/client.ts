import { ApiError, type ProblemDetail } from './errors';

export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080').replace(/\/$/, '');

type TokenSource = {
  get: () => string | null;
  /** Called once on a 401; returns a fresh token or null if the session is gone. */
  refresh: () => Promise<string | null>;
};

let tokenSource: TokenSource | null = null;

/** The auth provider registers itself so every request can attach / renew the access token. */
export function registerTokenSource(source: TokenSource | null) {
  tokenSource = source;
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Skip bearer token + refresh (public endpoints, auth endpoints). */
  anonymous?: boolean;
}

function buildUrl(path: string, query?: RequestOptions['query']) {
  const url = new URL(`${API_URL}/api${path.startsWith('/') ? path : `/${path}`}`);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
    }
  }
  return url.toString();
}

async function parseProblem(res: Response): Promise<ProblemDetail> {
  try {
    const body = await res.json();
    return { status: res.status, ...body };
  } catch {
    return { status: res.status, title: res.statusText };
  }
}

async function send(path: string, opts: RequestOptions, token: string | null) {
  const headers = new Headers(opts.headers);
  headers.set('Accept', 'application/json, application/problem+json');
  if (opts.body !== undefined && !(opts.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return fetch(buildUrl(path, opts.query), {
    ...opts,
    headers,
    credentials: 'include',
    body: opts.body === undefined ? undefined : opts.body instanceof FormData ? opts.body : JSON.stringify(opts.body),
  });
}

/** Typed fetch against the Spring Boot API. Throws ApiError (problem+json) on failure. */
export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const useAuth = !opts.anonymous && tokenSource;
  let res: Response;
  try {
    res = await send(path, opts, useAuth ? tokenSource!.get() : null);
    if (res.status === 401 && useAuth) {
      const fresh = await tokenSource!.refresh();
      if (fresh) res = await send(path, opts, fresh);
    }
  } catch {
    throw new ApiError(0, { status: 0, title: 'Network error' });
  }
  if (!res.ok) throw new ApiError(res.status, await parseProblem(res));
  if (res.status === 204) return undefined as T;
  const type = res.headers.get('content-type') ?? '';
  return (type.includes('json') ? res.json() : res.text()) as Promise<T>;
}

/** Server-side (RSC) fetch for public, cacheable data. Never sends credentials. */
export async function publicGet<T>(path: string, query?: RequestOptions['query']): Promise<T> {
  const res = await fetch(buildUrl(path, query), { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new ApiError(res.status, await parseProblem(res));
  return res.json() as Promise<T>;
}
