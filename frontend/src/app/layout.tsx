import type { Metadata, Viewport } from 'next';
import '@/design-system/styles/ngbcl-ds.css';
import './globals.css';
import { barlow, dmSans } from '@/design-system/fonts';
import { Providers } from './providers';

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000'),
  title: {
    default: 'NGBCL 1.0 — Next Gen Badminton Champions League',
    template: '%s · NGBCL 1.0',
  },
  description:
    'Next Gen Badminton Champions League 1.0 — Jaffna’s team badminton league. Rise. Rally. Rule the Court.',
  icons: {
    icon: [
      { url: '/brand/favicon.svg', type: 'image/svg+xml' },
      { url: '/brand/favicon.ico', sizes: '48x48' },
    ],
    apple: '/brand/apple-touch-icon.png',
  },
  openGraph: {
    siteName: 'NGBCL 1.0',
    images: [{ url: '/brand/hero-still-square.jpg', width: 1024, height: 1024 }],
  },
};

export const viewport: Viewport = {
  themeColor: '#101318',
  colorScheme: 'dark',
};

export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="en" className={`${barlow.variable} ${dmSans.variable}`}>
      <body>
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
