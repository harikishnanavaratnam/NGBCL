'use client';

import { useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { ConnectionPill } from '@/design-system/live/Live';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { teamColor } from '@/design-system/tokens';
import { errorMessage } from '@/lib/api/errors';
import { liveKey, usePublicLive, type PublicLive as Live, type PublicLiveMatch } from '@/lib/api/scoring';
import { formatDateTime } from '@/lib/format/dates';
import { useConnectionState, useTopic } from '@/lib/realtime/useTopic';
import s from './Public.module.css';

/** Keeps the cached /public/live snapshot current: list changes refetch, score changes patch in place. */
export function useLiveFeed() {
  const qc = useQueryClient();
  const q = usePublicLive();
  useTopic(
    '/topic/public/live',
    () => void qc.invalidateQueries({ queryKey: liveKey }),
    () => void qc.invalidateQueries({ queryKey: liveKey }),
  );
  return q;
}

function MatchTopic({ id }: { id: string }) {
  const qc = useQueryClient();
  useTopic<PublicLiveMatch>(
    `/topic/public/match/${id}`,
    (msg) =>
      qc.setQueryData<Live>(liveKey, (old) =>
        old
          ? { ...old, live: old.live.map((m) => (m.matchId === id && msg.payload.stateVersion >= m.stateVersion ? msg.payload : m)) }
          : old,
      ),
    () => void qc.invalidateQueries({ queryKey: liveKey }),
  );
  return null;
}

/** Public live scores (LIVE-001..004): matches on court now, recent results, next up. */
export function PublicLive() {
  const { data, isLoading, error, refetch } = useLiveFeed();
  const conn = useConnectionState();

  if (isLoading) {
    return (
      <section className={s.sec} style={{ borderTop: 0 }}>
        <div className="wrap" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          <Skeleton height={220} />
          <Skeleton height={220} />
        </div>
      </section>
    );
  }
  if (error || !data) {
    return (
      <section className={s.sec} style={{ borderTop: 0 }}>
        <div className="wrap">
          <ErrorState title="Couldn’t load live scores" message={errorMessage(error)} onRetry={() => void refetch()} />
        </div>
      </section>
    );
  }

  return (
    <>
      {data.live.map((m) => (
        <MatchTopic key={m.matchId} id={m.matchId} />
      ))}
      <section className={s.sec} style={{ borderTop: 0 }}>
        <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <h2 className={clsx('disp', s.h2)}>On court now</h2>
            <span style={{ marginLeft: 'auto' }}>
              <ConnectionPill state={conn} />
            </span>
          </div>
          {data.live.length === 0 ? (
            <EmptyState
              title="No matches on court right now"
              description={data.upNext.length ? 'The next matches are listed below.' : 'Live scores appear here as soon as a match starts.'}
              action={{ label: 'Fixtures', href: '/fixtures' }}
            />
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
              {data.live.map((m) => (
                <LiveCard key={m.matchId} m={m} />
              ))}
            </div>
          )}
        </div>
      </section>

      {data.recent.length > 0 && (
        <section className={clsx(s.sec, s.secAlt)}>
          <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h2 className={clsx('disp', s.h2)}>Latest results</h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
              {data.recent.map((m) => (
                <LiveCard key={m.matchId} m={m} />
              ))}
            </div>
          </div>
        </section>
      )}

      {data.upNext.length > 0 && (
        <section className={s.sec}>
          <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <h2 className={clsx('disp', s.h2)}>Up next</h2>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
              {data.upNext.map((m) => (
                <li
                  key={m.matchId}
                  className="card"
                  style={{
                    padding: '12px 16px',
                    display: 'grid',
                    gridTemplateColumns: 'minmax(0,1fr) auto',
                    gap: 12,
                    alignItems: 'center',
                  }}
                >
                  <span style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                    <strong>
                      {m.teamA.name} v {m.teamB.name}
                    </strong>
                    <span className="t-body-s muted">
                      {m.categoryName} · {names(m.sideA)} v {names(m.sideB)}
                    </span>
                  </span>
                  <span className="mono muted" style={{ fontSize: 12, textAlign: 'right' }}>
                    {m.scheduledAt ? formatDateTime(m.scheduledAt) : 'Time TBC'}
                    <br />
                    {m.courtName ?? ''}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}
    </>
  );
}

const names = (side: PublicLiveMatch['sideA']) => (side.length ? side.map((p) => p.fullName).join(' / ') : 'TBA');

function LiveCard({ m }: { m: PublicLiveMatch }) {
  const done = m.status === 'SUBMITTED' || m.status === 'COMPLETED';
  const prior = m.games.filter((g) => g.winnerSide !== null);
  return (
    <article
      className="card"
      style={{ padding: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
      aria-label={`${m.teamA.name} v ${m.teamB.name}, ${m.categoryName}`}
    >
      <header style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', borderBottom: '1px solid var(--line)' }}>
        {m.status === 'LIVE' ? (
          <span className="chip c-live">
            <span className="dot" />
            Live
          </span>
        ) : m.provisional ? (
          <span className="chip c-prov">Provisional</span>
        ) : (
          <span className="chip c-navy">Final</span>
        )}
        <span className="t-body-s" style={{ fontWeight: 600 }}>
          {m.categoryName}
        </span>
        <span className="mono muted" style={{ fontSize: 12, marginLeft: 'auto' }}>
          {m.courtName ?? ''} · {m.groupName ?? m.roundName}
        </span>
      </header>
      {(['A', 'B'] as const).map((side) => {
        const team = side === 'A' ? m.teamA : m.teamB;
        const players = side === 'A' ? m.sideA : m.sideB;
        const score = side === 'A' ? m.scoreA : m.scoreB;
        const games = side === 'A' ? m.gamesA : m.gamesB;
        const won = m.winnerSide === side;
        return (
          <div
            key={side}
            style={{
              display: 'grid',
              gridTemplateColumns: 'minmax(0,1fr) auto auto',
              gap: 12,
              alignItems: 'center',
              padding: '12px 14px',
              borderLeft: `4px solid ${teamColor(team.colorSlot)}`,
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
              <CrestTile logoUrl={team.logoUrl} name={team.name} colorSlot={team.colorSlot} size="s" />
              <span style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                <Link href={`/teams/${team.slug}`} style={{ fontWeight: 700, textDecoration: 'none' }}>
                  {team.name}
                  {m.server === side && m.status === 'LIVE' && (
                    <span
                      aria-label="serving"
                      title="Serving"
                      style={{
                        display: 'inline-block',
                        width: 8,
                        height: 8,
                        borderRadius: '50%',
                        background: 'var(--lime)',
                        marginLeft: 8,
                        verticalAlign: 'middle',
                      }}
                    />
                  )}
                </Link>
                <span className="t-caption" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {players.map((p, i) => (
                    <span key={i}>
                      {i > 0 && ' / '}
                      {p.slug ? <Link href={`/players/${p.slug}`}>{p.fullName}</Link> : p.fullName}
                    </span>
                  ))}
                </span>
              </span>
            </span>
            <span className="num" style={{ display: 'flex', gap: 8, fontSize: 15, color: 'var(--text-2)' }}>
              {prior.map((g) => (
                <span key={g.number} style={{ opacity: g.winnerSide === side ? 1 : 0.6 }}>
                  {side === 'A' ? g.scoreA : g.scoreB}
                </span>
              ))}
            </span>
            <span className="num" style={{ fontSize: done ? 28 : 40, minWidth: 44, textAlign: 'right', opacity: done && !won ? 0.6 : 1 }}>
              {done ? games : score}
            </span>
          </div>
        );
      })}
      {(m.outcome === 'WALKOVER' || m.outcome === 'RETIRED') && (
        <footer className="t-caption" style={{ padding: '8px 14px', borderTop: '1px solid var(--line)' }}>
          {m.outcome === 'WALKOVER' ? 'Walkover' : 'Retired'}
        </footer>
      )}
    </article>
  );
}
