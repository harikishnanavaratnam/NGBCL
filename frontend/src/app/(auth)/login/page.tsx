import Link from 'next/link';
import { Suspense } from 'react';
import { LoginForm } from '@/features/auth/LoginForm';

export const metadata = { title: 'Staff sign in', robots: { index: false } };

export default function LoginPage() {
  return (
    <main id="main" style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', padding: 16, background: 'radial-gradient(1200px 600px at 70% -10%, rgba(216,255,66,.06), transparent), var(--bg)' }}>
      <div className="card" style={{ width: '100%', maxWidth: 420, padding: 32, display: 'flex', flexDirection: 'column', gap: 24 }}>
        <Link href="/" aria-label="Back to the public site" style={{ alignSelf: 'flex-start' }}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/brand/ngbcl-wordmark-on-dark.svg" alt="NGBCL" style={{ height: 44, width: 'auto' }} />
        </Link>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span className="eyebrow">Staff only</span>
          <h1 className="t-h1">Sign in</h1>
          <p className="t-body-s muted">For organisers, auctioneers and match officials. Players and teams register without an account.</p>
        </div>
        <Suspense>
          <LoginForm />
        </Suspense>
      </div>
    </main>
  );
}
