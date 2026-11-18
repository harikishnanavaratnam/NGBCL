import type { NextConfig } from 'next';

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const wsUrl = apiUrl.replace(/^http/, 'ws');
const cdnUrl = process.env.NEXT_PUBLIC_CDN_URL ?? '';
/** Origin of the R2 / MinIO S3 endpoint: the browser PUTs uploads and loads private previews there. */
const storageOrigin = process.env.NEXT_PUBLIC_STORAGE_ORIGIN ?? '';

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
  cacheComponents: true,
  partialPrefetching: true,
  poweredByHeader: false,
  images: {
    remotePatterns: cdnUrl ? [new URL(`${cdnUrl}/**`)] : [],
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
