import localFont from 'next/font/local';

/**
 * Fonts are self-hosted (OFL, from @fontsource) so builds never depend on reaching Google Fonts.
 * Same families as the Claude Design export: Barlow Condensed (display, scores) + DM Sans (UI).
 */
export const barlow = localFont({
  variable: '--font-barlow',
  display: 'swap',
  src: [
    { path: './fonts/barlow-condensed-latin-500-normal.woff2', weight: '500', style: 'normal' },
    { path: './fonts/barlow-condensed-latin-600-normal.woff2', weight: '600', style: 'normal' },
    { path: './fonts/barlow-condensed-latin-700-normal.woff2', weight: '700', style: 'normal' },
    { path: './fonts/barlow-condensed-latin-800-normal.woff2', weight: '800', style: 'normal' },
    { path: './fonts/barlow-condensed-latin-600-italic.woff2', weight: '600', style: 'italic' },
    { path: './fonts/barlow-condensed-latin-700-italic.woff2', weight: '700', style: 'italic' },
    { path: './fonts/barlow-condensed-latin-800-italic.woff2', weight: '800', style: 'italic' },
  ],
});

export const dmSans = localFont({
  variable: '--font-dmsans',
  display: 'swap',
  src: [
    { path: './fonts/dm-sans-latin-wght-normal.woff2', weight: '100 1000', style: 'normal' },
    { path: './fonts/dm-sans-latin-wght-italic.woff2', weight: '100 1000', style: 'italic' },
  ],
});
