import { Hero } from '@/features/home/Hero';
import { PrizePool } from '@/features/home/PrizePool';
import { getPublicTournament } from '@/lib/api/public';
import { formatRs } from '@/lib/format/money';
import { formatDateRange } from '@/lib/format/range';

export default async function HomePage() {
  const t = await getPublicTournament();
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
      <section id="about" style={{ padding: '72px 0', borderTop: '1px solid rgba(245,247,250,.12)' }}>
        <div className="wrap" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 32 }}>
          <div>
            <span className="eyebrow">About the league</span>
            <h2 className="disp" style={{ fontSize: 'clamp(26px, 3.4vw, 40px)', marginTop: 6 }}>
              Jaffna’s team badminton league, <span className="gold" style={{ fontWeight: 700 }}>built on the rally</span>
            </h2>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <p className="t-body-l">
              {t?.description ??
                'NGBCL 1.0 is the inaugural edition of the Next Gen Badminton Champions League, presented by Mighty Sharks Battalion. Team owners build their squads through a live player auction, then meet in team fixtures made up of individual singles and doubles matches.'}
            </p>
            {t?.teamEntryFee != null && (
              <p className="t-body-s muted">
                Own a team — entry {formatRs(t.teamEntryFee)} per team. Information only; the organisers contact owners about the fee after review.
              </p>
            )}
          </div>
        </div>
      </section>
      <PrizePool prizes={t?.prizes} />
    </>
  );
}
