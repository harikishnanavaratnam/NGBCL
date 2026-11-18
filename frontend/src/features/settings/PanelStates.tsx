'use client';

import { ErrorState, Skeleton } from '@/design-system/data/States';
import { errorMessage } from '@/lib/api/errors';
import styles from './Settings.module.css';

export function PanelLoading() {
  return (
    <div className={styles.panel} aria-busy="true">
      <Skeleton height={28} width={200} />
      {Array.from({ length: 5 }, (_, i) => (
        <Skeleton key={i} height={44} />
      ))}
    </div>
  );
}

export function PanelError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <div className={styles.panel}>
      <ErrorState title="Couldn’t load settings" message={errorMessage(error)} onRetry={onRetry} />
    </div>
  );
}
