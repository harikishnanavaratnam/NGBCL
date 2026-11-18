'use client';

import { ErrorState } from '@/design-system/data/States';

export default function RouteError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main id="main" style={{ padding: 32, maxWidth: 720, margin: '64px auto' }}>
      <ErrorState title="Something went wrong" message={error.digest ? `Reference: ${error.digest}` : error.message} onRetry={reset} />
    </main>
  );
}
