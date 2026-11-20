import clsx from 'clsx';
import type { Metadata } from 'next';
import Link from 'next/link';
import { Suspense } from 'react';
import { EmptyState, Skeleton } from '@/design-system/data/States';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { teamColor } from '@/design-system/tokens';
import { Form } from '@/features/standings/StandingsTable';
import s from '@/features/public/Public.module.css';
import { PageBand, PlayerGrid } from '@/features/public/Sections';
import { getPublicTeam, staticSlugs, getPublicStats } from '@/lib/api/public';

export async function generateStaticParams() {
  return staticSlugs('teams');
}

export async function generateMetadata({ params }: PageProps<'/teams/[slug]'>): Promise<Metadata> {
  const team = await getPublicTeam((await params).slug);
  return team ? { title: team.name, description: team.description ?? `${team.name} — NGBCL 1.0 franchise.` } : { title: 'Team' };
}

export default function TeamPage(props: PageProps<'/teams/[slug]'>) {
  return (
    <Suspense
      fallback={
        <div className="wrap" style={{ padding: '56px 32px' }}>
          <Skeleton height={200} />
        </div>
      }
    >
      <TeamDetail params={props.params} />
    </Suspense>
  );
}

async function TeamDetail({ params }: Pick<PageProps<'/teams/[slug]'>, 'params'>) {
  const team = await getPublicTeam((await params).slug);
  const rec = team ? ((await getPublicStats()).teams.find((x) => x.team.slug === team.slug) ?? null) : null;
  if (!team) {
    return (
      <div className="wrap" style={{ padding: '64px 32px' }}>
        <EmptyState
          title="Team not found"
          description="This team doesn’t exist or isn’t public yet."
          action={{ label: 'All teams', href: '/teams' }}
        />
      </div>
    );
  }
  return (
    <>
      <div style={{ height: 6, background: teamColor(team.colorSlot) }} aria-hidden="true" />
      <PageBand
        crumb={
          <>
            <Link href="/teams">Teams</Link> / {team.name}
          </>
        }
        title={
          <span style={{ display: 'flex', alignItems: 'center', gap: 24, flexWrap: 'wrap' }}>
            <CrestTile logoUrl={team.logoUrl} name={team.name} colorSlot={team.colorSlot} size="xl" />
            {team.name}
          </span>
        }
      >
        <span className="mono muted">{team.shortCode}</span>
        {team.description && (
          <p className="muted" style={{ margin: 0, maxWidth: 640 }}>
            {team.description}
          </p>
        )}
      </PageBand>
      <section className={s.sec}>
        <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          <h2 className={clsx('disp', s.h2)}>Squad</h2>
          {team.squad.length ? (
            <PlayerGrid players={team.squad.map((m) => ({ ...m, teamColorSlot: team.colorSlot }))} hideTeam />
          ) : (
            <EmptyState
              title="Squad announced at the auction"
              description="Players join this team at the live player auction. Check back after auction day."
              action={{ label: 'Auction information', href: '/auction' }}
            />
          )}
        </div>
      </section>
      <section className={clsx(s.sec, s.secAlt)}>
        <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <h2 className={clsx('disp', s.h2)}>Record</h2>
          {rec ? (
            <>
              <div className={s.g4}>
                {[
                  ['Ties W–D–L', `${rec.tiesWon}–${rec.tiesDrawn}–${rec.tiesLost}`],
                  ['Matches won', `${rec.matchesWon}–${rec.matchesLost}`],
                  ['Games won', `${rec.gamesWon}–${rec.gamesLost}`],
                  ['Points scored', rec.pointsWon],
                ].map(([k, v]) => (
                  <div key={k} className={clsx('card', s.stat)}>
                    <span className="eyebrow">{k}</span>
                    <span className="num" style={{ fontSize: 32 }}>
                      {v}
                    </span>
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span className="eyebrow">Last 5</span>
                <Form form={rec.form} />
              </div>
            </>
          ) : (
            <p className="muted" style={{ margin: 0 }}>
              The record starts with this team’s first confirmed result.
            </p>
          )}
          <p style={{ margin: 0 }}>
            <Link href="/fixtures">Fixtures and results</Link> · <Link href="/standings">Standings</Link>
          </p>
        </div>
      </section>
    </>
  );
}
