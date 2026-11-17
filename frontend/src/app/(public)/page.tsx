import { Hero } from '@/features/home/Hero';
import { PrizePool } from '@/features/home/PrizePool';

export default function HomePage() {
  // M3 replaces this with GET /api/public/tournament (phase, dates, venue, registration window).
  return (
    <>
      <Hero data={{ phase: 'REGISTRATION', dates: null, venue: null, registrationClosesAt: null }} />
      <section id="about" style={{ padding: '72px 0', borderTop: '1px solid rgba(245,247,250,.12)' }}>
        <div className="wrap" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 32 }}>
          <div>
            <span className="eyebrow">About the league</span>
            <h2 className="disp" style={{ fontSize: 'clamp(26px, 3.4vw, 40px)', marginTop: 6 }}>
              Jaffna’s team badminton league, <span className="gold" style={{ fontWeight: 700 }}>built on the rally</span>
            </h2>
          </div>
          <p className="t-body-l">
            NGBCL 1.0 is the inaugural edition of the Next Gen Badminton Champions League, presented by Mighty Sharks Battalion. Team owners build
            their squads through a live player auction, then meet in team fixtures made up of individual singles and doubles matches.
          </p>
        </div>
      </section>
      <PrizePool />
    </>
  );
}
