import type { Metadata } from 'next';
import { Suspense } from 'react';
import { AdminShell } from '@/features/admin-shell/AdminShell';

export const metadata: Metadata = { title: 'Admin', robots: { index: false } };

/**
 * The admin portal is client-rendered behind sign-in. The shell reads the URL (usePathname),
 * which is request-time data, so it streams inside Suspense; the fallback is the static shell.
 */
export default function AdminLayout({ children }: LayoutProps<'/admin'>) {
  return (
    <Suspense fallback={<div style={{ minHeight: '100dvh', background: 'var(--bg)' }} aria-busy="true" />}>
      <AdminShell>{children}</AdminShell>
    </Suspense>
  );
}
