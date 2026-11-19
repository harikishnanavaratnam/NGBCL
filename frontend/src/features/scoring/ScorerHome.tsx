'use client';

import clsx from 'clsx';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { errorMessage } from '@/lib/api/errors';
import { useScorerMatches, type ScorerCard } from '@/lib/api/scoring';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime } from '@/lib/format/dates';
import styles from './Scorer.module.css';

/** Signed-in scorer (or official with "score any match") — everything else is sent to sign in. */
export function ScorerGate({ children }: { children: ReactNode }) {
  const { status, can } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  useEffect(() => {
    if (status === 'anonymous') router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [status, router, pathname]);
  if (status !== 'authenticated') {
    return (
      <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }} aria-busy="true">
        <Skeleton height={56} />
        <Skeleton height={420} />
      </div>
    );
  }
  if (!can('MATCH_SCORE', 'MATCH_SCORE_ANY')) {
    return (
      <div style={{ padding: 48 }}>
        <EmptyState
          title="You can’t score matches"
          description="Scoring needs the match scorer permission. Ask a Tournament Admin to grant it."
          action={{ label: 'Back to admin', href: '/admin' }}
        />
      </div>
    );
  }
  return <>{children}</>;
}

type Tab = 'todo' | 'submitted' | 'history';
const TABS: { key: Tab; label: string }[] = [
  { key: 'todo', label: 'My matches' },
  { key: 'submitted', label: 'Submitted' },
  { key: 'history', label: 'History' },
];
const inTab = (c: ScorerCard, t: Tab) =>
  t === 'todo'
    ? c.status === 'SCHEDULED' || c.status === 'LIVE'
    : t === 'submitted'
      ? c.status === 'SUBMITTED'
      : c.status === 'COMPLETED' || c.status === 'CANCELLED';

export function ScorerHome() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [tab, setTab] = useState<Tab>('todo');
  const { data, isLoading, error, refetch } = useScorerMatches();
  const rows = (data ?? []).filter((c) => inTab(c, tab));

  return (
    <div className={styles.app}>
      <header className={styles.top}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/brand/ngbcl-mark-on-dark.svg" alt="" aria-hidden="true" style={{ height: 32, width: 32 }} />
        <span className={styles.topTitle}>Scoring</span>
        <span className={styles.topMeta}>{user?.displayName}</span>
        <Button
          variant="ghost"
          size="sm"
          onClick={() =>
            void logout().then(() => {
              router.replace('/login');
            })
          }
        >
          Sign out
        </Button>
      </header>
      <main id="main" className={styles.list}>
        <h1 className="t-h2" style={{ margin: 0 }}>
          {TABS.find((t) => t.key === tab)?.label}
        </h1>
        {isLoading ? (
          Array.from({ length: 3 }, (_, i) => <Skeleton key={i} height={84} />)
        ) : error ? (
          <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState
            title={
              tab === 'todo' ? 'No matches to score' : tab === 'submitted' ? 'Nothing waiting for confirmation' : 'No finished matches yet'
            }
            description={
              tab === 'todo' ? 'Matches assigned to you by the tournament admin appear here once the fixtures are published.' : undefined
            }
          />
        ) : (
          rows.map((c) => <MatchCard key={c.matchId} c={c} />)
        )}
      </main>
      <nav aria-label="Scorer" className={styles.tabs}>
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={clsx(styles.tab, tab === t.key && styles.tabOn)}
            aria-current={tab === t.key ? 'page' : undefined}
            onClick={() => setTab(t.key)}
          >
            {t.label}
            {t.key === 'todo' && data && data.some((c) => c.returnedReason && c.status === 'LIVE') ? ' •' : ''}
          </button>
        ))}
      </nav>
    </div>
  );
}

function MatchCard({ c }: { c: ScorerCard }) {
  const players = (side: ScorerCard['sideA']) => (side.length ? side.map((p) => p.fullName).join(' / ') : 'Line-up not set');
  return (
    <Link href={`/score/${c.matchId}`} className={styles.card}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
        <span className={styles.cardMeta}>
          Tie {c.fixtureNumber} · {c.roundName}
          {c.groupName ? ` · ${c.groupName}` : ''} · Match {c.order} · {c.categoryName}
        </span>
        <span className={styles.cardTeams}>
          {c.teamA.name} v {c.teamB.name}
        </span>
        <span className={styles.cardMeta}>
          {players(c.sideA)} v {players(c.sideB)}
        </span>
        <span className={styles.cardMeta}>
          {c.courtName ?? 'Court TBC'} · {c.scheduledAt ? formatDateTime(c.scheduledAt) : 'Time TBC'}
        </span>
        {c.returnedReason && c.status === 'LIVE' && (
          <span className="t-caption" style={{ color: 'var(--warn)' }}>
            Returned: {c.returnedReason}
          </span>
        )}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 8 }}>
        <StatusChip status={c.status}>
          {c.status === 'SCHEDULED' ? 'Not started' : c.status === 'SUBMITTED' ? 'Awaiting confirmation' : undefined}
        </StatusChip>
        {c.status !== 'SCHEDULED' && (
          <span className="num" style={{ fontSize: 22 }}>
            {c.gamesA}–{c.gamesB}
          </span>
        )}
        {!c.lineupComplete && c.status === 'SCHEDULED' && <span className="t-caption">Line-up incomplete</span>}
      </div>
    </Link>
  );
}
