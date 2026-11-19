'use client';

import clsx from 'clsx';
import Link from 'next/link';
import { useMemo } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { ConnectionPill } from '@/design-system/live/Live';
import { teamColor } from '@/design-system/tokens';
import { usePublicAuctionResults, usePublicAuctionState, type PublicBuy } from '@/lib/api/auction';
import { errorMessage } from '@/lib/api/errors';
import { formatDateTime } from '@/lib/format/dates';
import { formatRs } from '@/lib/format/money';
import { useConnectionState } from '@/lib/realtime/useTopic';
import { useAuctionLive } from '../auction/useAuctionLive';
import s from './Public.module.css';

/** Public auction page: live lot while bidding, then results grouped by team. */
export function PublicAuction() {
  const state = usePublicAuctionState();
  const results = usePublicAuctionResults();
  const conn = useConnectionState();
  useAuctionLive({
    onEvent: (type) => {
      if (type.startsWith('LOT_') || type === 'CORRECTION' || type === 'ENDED' || type === 'STARTED') void results.refetch();
    },
  });

  const byTeam = useMemo(() => {
    const m = new Map<string, PublicBuy[]>();
    for (const b of results.data?.sold ?? []) {
      const k = b.teamSlug ?? '';
      m.set(k, [...(m.get(k) ?? []), b]);
    }
    return m;
  }, [results.data]);

  if (state.isLoading || results.isLoading) {
    return (
      <section className={s.sec} style={{ borderTop: 0 }}>
        <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Skeleton height={200} />
          <Skeleton height={320} />
        </div>
      </section>
    );
  }
  if (state.error || !state.data || !results.data) {
    return (
      <section className={s.sec} style={{ borderTop: 0 }}>
        <div className="wrap">
          <ErrorState title="Couldn’t load the auction" message={errorMessage(state.error ?? results.error)} onRetry={() => void Promise.all([state.refetch(), results.refetch()])} />
        </div>
      </section>
    );
  }

  const st = state.data;
  const res = results.data;
  const lot = st.currentLot;

  if (st.status === 'NOT_STARTED') {
    return (
      <section className={s.sec} style={{ borderTop: 0 }}>
        <div className="wrap">
          <EmptyState
            title={st.startsAt ? `The auction starts ${formatDateTime(st.startsAt)}` : 'Auction date to be announced'}
            description="Once bidding starts, every sale appears here live. Meet the teams and players in the meantime."
            action={{ label: 'See the players', href: '/players' }}
          />
        </div>
      </section>
    );
  }

  return (
    <>
      {(st.status === 'LIVE' || st.status === 'PAUSED') && (
        <section className={s.sec} style={{ borderTop: 0 }}>
          <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <span className={clsx('chip', st.status === 'LIVE' ? 'c-live' : 'c-pend')}>{st.status === 'LIVE' ? 'Live now' : 'Short break'}</span>
              <span className="mono muted">
                Lot {st.lotsOpened || '—'} of {st.totalLots}
              </span>
              <span style={{ marginLeft: 'auto' }}>
                <ConnectionPill state={conn} />
              </span>
            </div>
            {lot ? (
              <div className="card" style={{ padding: 20, display: 'flex', gap: 20, alignItems: 'center', flexWrap: 'wrap' }} aria-live="polite">
                {lot.photoUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={lot.photoUrl} alt={lot.fullName ?? ''} style={{ width: 96, height: 120, borderRadius: 8, objectFit: 'cover' }} />
                ) : (
                  <span className="ph-person" style={{ width: 96, height: 120, borderRadius: 8, display: 'block' }} aria-hidden="true" />
                )}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: 1, minWidth: 200 }}>
                  <span className="eyebrow">On the block · lot {lot.lotNumber}</span>
                  <strong style={{ fontSize: 22 }}>{lot.fullName}</strong>
                  {lot.categoryName && <span className="chip c-neutral" style={{ alignSelf: 'flex-start' }}>{lot.categoryName}</span>}
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                  <span className="eyebrow">{lot.currentAmount === null ? 'Base price' : 'Current bid'}</span>
                  <span className="num" style={{ fontSize: 40, color: 'var(--lime)' }}>
                    {formatRs(lot.currentAmount ?? lot.basePrice)}
                  </span>
                  {lot.leadingTeam && <span className="muted">{lot.leadingTeam.name} leads</span>}
                </div>
              </div>
            ) : (
              <p className="muted" style={{ margin: 0 }}>
                {st.status === 'PAUSED' ? 'Bidding resumes shortly.' : 'Next player coming up…'}
              </p>
            )}
          </div>
        </section>
      )}

      <section className={clsx(s.sec, s.secAlt)}>
        <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <h2 className={clsx('disp', s.h2)}>{st.status === 'COMPLETED' ? 'Auction results' : 'Squads so far'}</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
            {res.teams.map((t) => {
              const buys = byTeam.get(t.slug) ?? [];
              return (
                <div key={t.slug} className="card" style={{ padding: 18, borderTop: `4px solid ${teamColor(t.colorSlot)}`, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
                    <Link href={`/teams/${t.slug}`} style={{ fontWeight: 700, fontSize: 18 }}>
                      {t.name}
                    </Link>
                    <span className="muted" style={{ marginLeft: 'auto', fontSize: 13 }}>
                      {t.squadCount}/{t.maxSquadSize}
                      {res.budgetsPublic && t.remaining !== null ? ` · ${formatRs(t.remaining)} left` : ''}
                    </span>
                  </div>
                  {buys.length === 0 && <span className="muted">No players yet.</span>}
                  {buys.map((b) => (
                    <div key={b.slug ?? b.fullName} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14 }}>
                      {b.slug ? <Link href={`/players/${b.slug}`}>{b.fullName}</Link> : <span>{b.fullName}</span>}
                      {b.categoryName && <span className="muted">· {b.categoryName}</span>}
                      {res.pricesPublic && b.price !== null && (
                        <span className="num gold" style={{ marginLeft: 'auto' }}>
                          {formatRs(b.price)}
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
          {st.status === 'COMPLETED' && res.unsold.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <h3 className="t-h3">Unsold</h3>
              <p className="muted" style={{ margin: 0 }}>{res.unsold.map((u) => u.fullName).join(' · ')}</p>
            </div>
          )}
        </div>
      </section>
    </>
  );
}
