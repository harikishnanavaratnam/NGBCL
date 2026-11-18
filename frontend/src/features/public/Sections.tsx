import clsx from 'clsx';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { PlayerCard } from '@/design-system/sport/PlayerCard';
import { teamColor } from '@/design-system/tokens';
import type { PublicNewsItem, PublicPlayer, PublicSponsor, PublicTeam, SponsorTier } from '@/lib/api/public';
import { formatDate } from '@/lib/format/dates';
import s from './Public.module.css';

export function PageBand({
  crumb,
  eyebrow,
  title,
  children,
}: {
  crumb?: ReactNode;
  eyebrow?: ReactNode;
  title: ReactNode;
  children?: ReactNode;
}) {
  return (
    <section className={s.band}>
      <div className={clsx('wrap', s.bandInner)}>
        {crumb && <div className={clsx('eyebrow', s.crumb)}>{crumb}</div>}
        {eyebrow && !crumb && <div className="eyebrow">{eyebrow}</div>}
        <h1 className={clsx('disp', s.h1)}>{title}</h1>
        {children}
      </div>
    </section>
  );
}

export function SectionHead({ eyebrow, title, link }: { eyebrow: string; title: ReactNode; link?: { href: string; label: string } }) {
  return (
    <div className={s.secHead}>
      <div className={s.secTitle}>
        <div className="eyebrow">{eyebrow}</div>
        <h2 className={clsx('disp', s.h2)}>{title}</h2>
      </div>
      {link && (
        <Link href={link.href} className="link">
          {link.label}
        </Link>
      )}
    </div>
  );
}

export function TeamGrid({ teams }: { teams: PublicTeam[] }) {
  return (
    <div className={s.gTeams}>
      {teams.map((t) => (
        <Link
          key={t.slug}
          href={`/teams/${t.slug}`}
          prefetch
          className={clsx('card', s.teamCard)}
          style={{ ['--band' as string]: teamColor(t.colorSlot) }}
        >
          <CrestTile logoUrl={t.logoUrl} name={t.name} colorSlot={t.colorSlot} size="l" />
          <strong style={{ fontSize: 14 }}>{t.name}</strong>
          <span className="mono muted" style={{ fontSize: 12 }}>
            {t.shortCode}
          </span>
        </Link>
      ))}
    </div>
  );
}

export function PlayerGrid({ players }: { players: PublicPlayer[] }) {
  return (
    <div className={s.gPlayers}>
      {players.map((p) => (
        <Link key={p.slug} href={`/players/${p.slug}`} prefetch className={s.cardLink}>
          <PlayerCard p={{ fullName: p.fullName, photoUrl: p.photoUrl, category: p.categoryName, teamName: 'Not yet auctioned' }} />
        </Link>
      ))}
    </div>
  );
}

export function NewsCard({ n }: { n: PublicNewsItem }) {
  return (
    <Link href={`/news/${n.slug}`} prefetch className={s.cardLink}>
      <article className={clsx('card', s.news)}>
        {n.coverUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img className={s.newsCover} src={n.coverUrl} alt="" loading="lazy" />
        )}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
          {n.pinned ? <span className={clsx('chip', s.pinned)}>Pinned</span> : <span />}
          <span className="mono muted" style={{ fontSize: 12 }}>
            {formatDate(n.publishedAt)}
          </span>
        </div>
        <h3 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>{n.title}</h3>
        <p className="muted" style={{ margin: 0, fontSize: 14 }}>
          {n.excerpt}
        </p>
        <span className="link" style={{ marginTop: 'auto' }}>
          Read more
        </span>
      </article>
    </Link>
  );
}

const TIERS: { tier: SponsorTier; label: string; color: string; height: number; min: number }[] = [
  { tier: 'PLATINUM', label: 'Platinum', color: '#D9DEE7', height: 72, min: 220 },
  { tier: 'GOLD', label: 'Gold', color: '#D6B877', height: 56, min: 160 },
  { tier: 'SILVER', label: 'Silver', color: '#A5ADB8', height: 44, min: 130 },
  { tier: 'BRONZE', label: 'Bronze', color: '#B8733A', height: 44, min: 130 },
  { tier: 'MEDIA', label: 'Official media partner', color: 'rgba(245,247,250,.4)', height: 44, min: 130 },
];

function SponsorLogo({ sp, height }: { sp: PublicSponsor; height: number }) {
  const inner = sp.logoUrl ? (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={sp.logoUrl} alt={sp.name} loading="lazy" style={{ height: height - 20 }} />
  ) : (
    <span>{sp.name}</span>
  );
  return sp.websiteUrl ? (
    <a
      href={sp.websiteUrl}
      target="_blank"
      rel="noopener noreferrer sponsored"
      className={s.logoTile}
      style={{ height }}
      title={sp.awardName ?? sp.name}
    >
      {inner}
    </a>
  ) : (
    <span className={s.logoTile} style={{ height }} title={sp.awardName ?? sp.name}>
      {inner}
    </span>
  );
}

/** Sponsor wall grouped by tier; tiers without a sponsor are hidden (export Main · 11). */
export function SponsorWall({ sponsors, contactHref }: { sponsors: PublicSponsor[]; contactHref: string }) {
  const wall = sponsors.filter((x) => x.showOnWall);
  const title = wall.find((x) => x.tier === 'TITLE');
  return (
    <section className={clsx(s.sec)} id="sponsors" aria-labelledby="sponsors-title">
      <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
        <div className={s.secHead} style={{ marginBottom: 0 }}>
          <div className={s.secTitle}>
            <div className="eyebrow">Partners</div>
            <h2 id="sponsors-title" className={clsx('disp', s.h2)}>
              Our sponsors
            </h2>
          </div>
          <a href={contactHref} className="link">
            Become a sponsor
          </a>
        </div>
        {wall.length === 0 ? (
          <div className="card" style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'flex-start' }}>
            <strong style={{ fontSize: 18 }}>Partner with NGBCL 1.0</strong>
            <span className="muted">Title, platinum, gold and media partnerships are open. Get in touch with the organisers.</span>
            <a href={contactHref} className="btn btn-volt">
              Become a sponsor
            </a>
          </div>
        ) : (
          <>
            {title && (
              <div className={clsx('card', s.titleSponsor)}>
                <span className="eyebrow" style={{ color: 'var(--gold-2)' }}>
                  Title sponsor
                </span>
                <SponsorLogo sp={title} height={96} />
              </div>
            )}
            {TIERS.map((t) => {
              const list = wall.filter((x) => x.tier === t.tier);
              if (!list.length) return null;
              return (
                <div key={t.tier} className={s.tier} style={{ ['--tier' as string]: t.color }}>
                  <span className="eyebrow">{t.label}</span>
                  <div className={s.logos} style={{ gridTemplateColumns: `repeat(auto-fill, minmax(${t.min}px, 1fr))` }}>
                    {list.map((sp) => (
                      <SponsorLogo key={sp.name} sp={sp} height={t.height} />
                    ))}
                  </div>
                </div>
              );
            })}
          </>
        )}
      </div>
    </section>
  );
}

export function TitleLockup({ sponsor, contactHref }: { sponsor: PublicSponsor | undefined; contactHref: string }) {
  return (
    <div className={s.lockup}>
      <div className={clsx('wrap', s.lockupInner)}>
        <span className="eyebrow">NGBCL 1.0 presented by</span>
        {sponsor ? (
          sponsor.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={sponsor.logoUrl} alt={sponsor.name} />
          ) : (
            <strong>{sponsor.name}</strong>
          )
        ) : (
          <span className="muted" style={{ fontSize: 13 }}>
            Title sponsorship available ·{' '}
            <a href={contactHref} className="link">
              Become a sponsor
            </a>
          </span>
        )}
      </div>
    </div>
  );
}
