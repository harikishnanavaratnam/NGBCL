import clsx from 'clsx';
import type { Metadata } from 'next';
import Link from 'next/link';
import { Suspense } from 'react';
import { EmptyState, Skeleton } from '@/design-system/data/States';
import { Money } from '@/design-system/primitives/Text';
import { teamColor } from '@/design-system/tokens';
import s from '@/features/public/Public.module.css';
import { getPublicPlayer, getPublicStats, staticSlugs } from '@/lib/api/public';
import { splitName } from '@/lib/format/names';

export async function generateStaticParams() {
  return staticSlugs('players');
}

export async function generateMetadata({ params }: PageProps<'/players/[slug]'>): Promise<Metadata> {
  const p = await getPublicPlayer((await params).slug);
  return p
    ? { title: p.fullName, description: `${p.fullName}${p.categoryName ? ` · ${p.categoryName}` : ''} — NGBCL 1.0 player.` }
    : { title: 'Player' };
}

export default function PlayerPage(props: PageProps<'/players/[slug]'>) {
  return (
    <Suspense
      fallback={
        <div className="wrap" style={{ padding: '56px 32px' }}>
          <Skeleton height={360} />
        </div>
      }
    >
      <PlayerDetail params={props.params} />
    </Suspense>
  );
}

async function PlayerDetail({ params }: Pick<PageProps<'/players/[slug]'>, 'params'>) {
  const p = await getPublicPlayer((await params).slug);
  if (!p) {
    return (
      <div className="wrap" style={{ padding: '64px 32px' }}>
        <EmptyState
          title="Player not found"
          description="This player doesn’t exist or isn’t public yet."
          action={{ label: 'All players', href: '/players' }}
        />
      </div>
    );
  }
  const { first, last } = splitName(p.fullName);
  const rec = (await getPublicStats()).players.find((x) => x.slug === p.slug) ?? null;
  return (
    <>
      <section className={s.band}>
        <div className={clsx('wrap', s.profile)}>
          {p.photoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img className={s.photo} src={p.photoUrl} alt={p.fullName} />
          ) : (
            <div className={s.initials} aria-hidden="true">
              {first.charAt(0)}
              {last.charAt(0)}
            </div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className={clsx('eyebrow', s.crumb)}>
              <Link href="/players">Players</Link> / {p.fullName}
            </div>
            <div>
              <span style={{ fontSize: 22 }}>{first}</span>
              <h1 className={clsx('disp', s.h1)}>{last || first}</h1>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
              {p.categoryName && <span className="chip c-neutral">{p.categoryName}</span>}
              {p.teamSlug && p.teamName ? (
                <Link href={`/teams/${p.teamSlug}`} className="chip" style={{ background: teamColor(p.teamColorSlot), color: '#fff' }}>
                  {p.teamName}
                </Link>
              ) : (
                <span className="muted">Not yet auctioned</span>
              )}
              {p.price !== null && p.teamSlug && (
                <span className="muted">
                  Sold for <Money value={p.price} variant="public" className="gold" />
                  {p.lotNumber ? ` · lot ${p.lotNumber}` : ''}
                </span>
              )}
            </div>
            {p.experience && (
              <p className="muted" style={{ margin: 0, maxWidth: 620 }}>
                {p.experience}
              </p>
            )}
          </div>
        </div>
      </section>
      <section className={s.sec}>
        <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <h2 className={clsx('disp', s.h2)}>Statistics</h2>
          <div className={s.g4}>
            {[
              ['Matches', rec ? rec.played : null],
              ['Won', rec ? rec.won : null],
              ['Games won', rec ? `${rec.gamesWon}–${rec.gamesLost}` : null],
              ['Points scored', rec ? rec.pointsWon : null],
            ].map(([k, v]) => (
              <div key={k} className={clsx('card', s.stat)}>
                <span className="eyebrow">{k}</span>
                <span className="num" style={{ fontSize: 32 }}>
                  {v ?? '—'}
                </span>
              </div>
            ))}
          </div>
          {rec && rec.categories.length > 0 && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {rec.categories.map((c) => (
                <span key={c.code} className="chip c-neutral">
                  {c.name}: {c.won} of {c.played} won
                </span>
              ))}
              {rec.walkoversWon > 0 && <span className="chip c-neutral">Walkovers: {rec.walkoversWon}</span>}
            </div>
          )}
          <p className="help" style={{ margin: 0 }}>
            Statistics count confirmed results only and start once matches are played.
          </p>
        </div>
      </section>
    </>
  );
}
