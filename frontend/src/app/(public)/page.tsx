import clsx from 'clsx';
import Link from 'next/link';
import { Hero } from '@/features/home/Hero';
import { PrizePool } from '@/features/home/PrizePool';
import { organiserContactHref } from '@/features/public/contact';
import s from '@/features/public/Public.module.css';
import { NewsCard, PlayerGrid, SectionHead, SponsorWall, TeamGrid, TitleLockup } from '@/features/public/Sections';
import { getNews, getPublicPlayers, getPublicTeams, getPublicTournament, getSponsors } from '@/lib/api/public';
import { formatRs } from '@/lib/format/money';
import { formatDateRange } from '@/lib/format/range';

export default async function HomePage() {
  const [t, teams, players, sponsors, news] = await Promise.all([
    getPublicTournament(),
    getPublicTeams(),
    getPublicPlayers(),
    getSponsors(),
    getNews(0, 3),
  ]);
  const contact = organiserContactHref(t);
  return (
    <>
      <Hero
        data={{
          phase: t?.phase ?? 'PRE_LAUNCH',
          dates: t ? formatDateRange(t.startsOn, t.endsOn) : null,
          venue: t?.venue ?? null,
          city: t?.city,
          registrationClosesAt: t?.playerRegistration.closesAt ?? null,
          presenterLine: t?.presenterLine,
          tagline: t?.tagline,
          logoUrl: t?.branding.logoUrl,
          teamRegistrationOpen: t?.teamRegistration.state === 'OPEN',
        }}
      />
      <TitleLockup sponsor={sponsors.find((x) => x.tier === 'TITLE' && x.showOnWall)} contactHref={contact} />
      <section id="about" style={{ padding: '72px 0', borderTop: '1px solid rgba(245,247,250,.12)' }}>
        <div className="wrap" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 32 }}>
          <div>
            <span className="eyebrow">About the league</span>
            <h2 className="disp" style={{ fontSize: 'clamp(26px, 3.4vw, 40px)', marginTop: 6 }}>
              Jaffna’s team badminton league,{' '}
              <span className="gold" style={{ fontWeight: 700 }}>
                built on the rally
              </span>
            </h2>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <p className="t-body-l">
              {t?.description ??
                'NGBCL 1.0 is the inaugural edition of the Next Gen Badminton Champions League, presented by Mighty Sharks Battalion. Team owners build their squads through a live player auction, then meet in team fixtures made up of individual singles and doubles matches.'}
            </p>
            {t?.teamEntryFee != null && (
              <p className="t-body-s muted">
                Own a team — entry {formatRs(t.teamEntryFee)} per team. Information only; the organisers contact owners about the fee after
                review.
              </p>
            )}
          </div>
        </div>
      </section>
      <PrizePool prizes={t?.prizes} />

      <section className={s.sec} id="teams" aria-label="Teams">
        <div className="wrap">
          <SectionHead eyebrow="The franchises" title="Teams" link={teams.length ? { href: '/teams', label: 'All teams' } : undefined} />
          {teams.length ? (
            <TeamGrid teams={teams} />
          ) : (
            <p className="muted" style={{ margin: 0 }}>
              Teams are announced after review.{' '}
              {t?.teamRegistration.state === 'OPEN' && (
                <Link href="/register/team" className="link">
                  Enter a team
                </Link>
              )}
            </p>
          )}
        </div>
      </section>

      <section className={clsx(s.sec, s.secAlt)} id="players" aria-label="Players">
        <div className="wrap">
          <SectionHead
            eyebrow="Approved players"
            title="Players"
            link={players.length ? { href: '/players', label: 'All players' } : undefined}
          />
          {players.length ? (
            <PlayerGrid players={players.slice(0, 10)} />
          ) : (
            <p className="muted" style={{ margin: 0 }}>
              Approved players appear here after review.{' '}
              {t?.playerRegistration.state === 'OPEN' && (
                <Link href="/register/player" className="link">
                  Register as a player
                </Link>
              )}
            </p>
          )}
        </div>
      </section>

      <SponsorWall sponsors={sponsors} contactHref={contact} />

      {news.content.length > 0 && (
        <section className={clsx(s.sec, s.secAlt)} id="news" aria-label="Announcements">
          <div className="wrap">
            <SectionHead eyebrow="From the organisers" title="Announcements" link={{ href: '/news', label: 'All news' }} />
            <div className={s.g3}>
              {news.content.map((n) => (
                <NewsCard key={n.slug} n={n} />
              ))}
            </div>
          </div>
        </section>
      )}
    </>
  );
}
