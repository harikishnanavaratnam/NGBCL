import Link from 'next/link';
import { CourtArt } from '@/design-system/data/States';

export default function NotFound() {
  return (
    <main id="main" style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', padding: 24, textAlign: 'center' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 20 }}>
        <CourtArt width={220} />
        <span className="eyebrow">404</span>
        <h1 className="disp t-display-l">Out of court</h1>
        <p className="muted">That page doesn’t exist — or it isn’t published yet.</p>
        <Link href="/" className="btn btn-volt">
          Back to home
        </Link>
      </div>
    </main>
  );
}
