import type { NextConfig } from 'next';

/**
 * Production on Vercel: set API_PROXY_TARGET (e.g. https://ngbcl-api.duckdns.org) and NEXT_PUBLIC_WS_URL
 * (wss://ngbcl-api.duckdns.org/ws). This site's /api/* is then proxied to the API, so the browser only
 * talks to one origin for REST and the sign-in cookies are first-party. Locally neither is set.
 */
const proxyTarget = (process.env.API_PROXY_TARGET ?? '').replace(/\/$/, '');
const apiUrl = proxyTarget ? '' : (process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const wsUrl = process.env.NEXT_PUBLIC_WS_URL ?? apiUrl.replace(/^http/, 'ws');
const cdnUrl = process.env.NEXT_PUBLIC_CDN_URL ?? '';
/** Origin of the R2 / MinIO S3 endpoint: the browser PUTs uploads and loads private previews there. */
const storageOrigin = process.env.NEXT_PUBLIC_STORAGE_ORIGIN ?? '';

const hsts = process.env.NODE_ENV === 'production' && proxyTarget;

const csp = [
  "default-src 'self'",
  `connect-src 'self' ${apiUrl} ${wsUrl} ${storageOrigin}`.trim(),
  // apiUrl: local-disk dev storage serves previews from the API itself
  `img-src 'self' data: blob: ${apiUrl} ${cdnUrl} ${storageOrigin}`.trim(),
  `media-src 'self' ${cdnUrl}`.trim(),
  "style-src 'self' 'unsafe-inline'",
  "script-src 'self' 'unsafe-inline' https://challenges.cloudflare.com" + (process.env.NODE_ENV === 'development' ? " 'unsafe-eval'" : ''),
  'frame-src https://challenges.cloudflare.com',
  "font-src 'self'",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ');

const nextConfig: NextConfig = {
  // The browser bundle must not point at the API host when the site proxies it.
  env: { NEXT_PUBLIC_API_URL: apiUrl },
  cacheComponents: true,
  partialPrefetching: true,
  poweredByHeader: false,
  images: {
    remotePatterns: cdnUrl ? [new URL(`${cdnUrl}/**`)] : [],
  },
  async rewrites() {
    // Runs after this app's own routes (e.g. /api/revalidate), so only unknown /api/* paths are proxied.
    return proxyTarget ? [{ source: '/api/:path*', destination: `${proxyTarget}/api/:path*` }] : [];
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'Content-Security-Policy', value: csp },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'Permissions-Policy', value: 'camera=(), microphone=(), geolocation=()' },
          { key: 'X-Frame-Options', value: 'DENY' },
          ...(hsts ? [{ key: 'Strict-Transport-Security', value: 'max-age=31536000; includeSubDomains' }] : []),
        ],
      },
      {
        source: '/brand/:file*',
        headers: [{ key: 'Cache-Control', value: 'public, max-age=604800, stale-while-revalidate=86400' }],
      },
    ];
  },
};

export default nextConfig;
