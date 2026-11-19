import clsx from 'clsx';
import Link from 'next/link';
import { EmptyState } from '@/design-system/data/States';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { teamColor } from '@/design-system/tokens';
import s from '@/features/public/Public.module.css';
import { PageBand } from '@/features/public/Sections';
import { getPublicFixtures, type PublicFixture, type PublicFixtureTeam } from '@/lib/api/public';
import { formatDate, formatDateTime } from '@/lib/format/dates';

export const metadata = {
  title: 'Fixtures',
  description: 'NGBCL 1.0 team fixtures: every tie, its matches, dates and courts.',
};

/** Public fixtures (FIX-010): published ties grouped by day; each tie expands to its matches. */
export default async function FixturesPage() {
  const data = await getPublicFixtures();
  const byDay = new Map<string, PublicFixture[]>();
  for (const f of data.fixtures) {
    const k = f.scheduledAt ? formatDate(f.scheduledAt) : 'Date to be confirmed';
    byDay.set(k, [...(byDay.get(k) ?? []), f]);
  }
  const ko = data.fixtures.filter((f) => f.stageType === 'KNOCKOUT');
  return (
    <>
      <PageBand
        crumb={
          <>
            <Link href="/">Home</Link> / Fixtures
          </>
        }
        eyebrow="Competition"
        title="Fixtures"
      >
        <p style={{ margin: 0, maxWidth: 640 }}>Each tie is a team-versus-team fixture made up of individual singles and doubles matches. Open a tie to see who plays.</p>
      </PageBand>

      {data.fixtures.length === 0 ? (
        <section className={s.sec} style={{ borderTop: 0 }}>
          <div className="wrap">
            <EmptyState title="Fixtures are on their way" description="The schedule is published after the player auction. Check back soon." action={{ label: 'Auction results', href: '/auction' }} />
          </div>
        </section>
      ) : (
        <>
          {data.groups.length > 0 && (
            <section className={s.sec} style={{ borderTop: 0 }}>
              <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <h2 className={clsx('disp', s.h2)}>Groups</h2>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 16 }}>
                  {data.groups.map((g) => (
                    <div key={g.name} className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
                      <strong>{g.name}</strong>
                      {g.teams.map((t) => (
                        <Link key={t.slug} href={`/teams/${t.slug}`} style={{ display: 'flex', alignItems: 'center', gap: 10, textDecoration: 'none' }}>
                          <CrestTile logoUrl={t.logoUrl} name={t.name} colorSlot={t.colorSlot} size="s" />
                          {t.name}
                        </Link>
                      ))}
                    </div>
                  ))}
                </div>
              </div>
            </section>
          )}

          <section className={clsx(s.sec, data.groups.length > 0 && s.secAlt)} style={data.groups.length ? undefined : { borderTop: 0 }}>
            <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
              <h2 className={clsx('disp', s.h2)}>Schedule</h2>
              {[...byDay.entries()].map(([day, fs]) => (
                <div key={day} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <h3 className="eyebrow" style={{ margin: 0 }}>
                    {day}
                  </h3>
                  {fs.map((f) => (
                    <TieCard key={f.number} f={f} />
                  ))}
                </div>
              ))}
            </div>
          </section>

          {ko.length > 0 && (
            <section className={s.sec}>
              <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                <h2 className={clsx('disp', s.h2)}>Knockout</h2>
                <div style={{ display: 'grid', gridAutoFlow: 'column', gridAutoColumns: 'minmax(220px, 1fr)', gap: 20, overflowX: 'auto' }}>
                  {[...new Set(ko.map((f) => f.roundName))].map((round) => (
                    <div key={round} style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-around', gap: 14 }}>
                      <span className="eyebrow">{round}</span>
                      {ko
                        .filter((f) => f.roundName === round)
                        .map((f) => (
                          <div key={f.number} className="card" style={{ overflow: 'hidden' }}>
                            <BracketSide team={f.teamA} source={f.sourceA} />
                            <BracketSide team={f.teamB} source={f.sourceB} top />
                            <div className="mono muted" style={{ fontSize: 12, padding: '6px 10px', borderTop: '1px solid var(--line)' }}>
                              #{f.number} · {f.scheduledAt ? formatDateTime(f.scheduledAt) : 'Date TBC'}
                            </div>
                          </div>
                        ))}
                    </div>
                  ))}
                </div>
              </div>
            </section>
          )}
        </>
      )}
    </>
  );
}

function TeamSide({ team, source, right }: { team: PublicFixtureTeam | null; source: string | null; right?: boolean }) {
  if (!team) {
    return <span style={{ fontStyle: 'italic', color: 'var(--text-2)', textAlign: right ? 'right' : 'left' }}>{source ?? 'To be decided'}</span>;
  }
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 10, justifyContent: right ? 'flex-end' : 'flex-start', minWidth: 0 }}>
      {!right && <CrestTile logoUrl={team.logoUrl} name={team.name} colorSlot={team.colorSlot} size="s" />}
      <strong style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{team.name}</strong>
      {right && <CrestTile logoUrl={team.logoUrl} name={team.name} colorSlot={team.colorSlot} size="s" />}
    </span>
  );
}

function TieCard({ f }: { f: PublicFixture }) {
  const time = f.scheduledAt ? formatDateTime(f.scheduledAt).split(' · ')[1] : null;
  return (
    <details className="card" style={{ padding: 0, borderLeft: `4px solid ${teamColor(f.teamA?.colorSlot)}` }}>
      <summary style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) auto minmax(0, 1fr)', gap: 12, alignItems: 'center', padding: '14px 16px', cursor: 'pointer' }}>
        <TeamSide team={f.teamA} source={f.sourceA} />
        <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          <span className="num" style={{ fontSize: 18 }}>
            {time ?? 'TBC'}
          </span>
          <span className="mono muted" style={{ fontSize: 11 }}>
            {f.groupName ?? f.roundName}
            {f.courtName ? ` · ${f.courtName}` : ''}
          </span>
        </span>
        <TeamSide team={f.teamB} source={f.sourceB} right />
      </summary>
      <ol style={{ listStyle: 'none', margin: 0, padding: '0 16px 14px', display: 'flex', flexDirection: 'column', gap: 6 }}>
        {f.matches.map((m) => (
          <li key={m.order} style={{ display: 'grid', gridTemplateColumns: '56px minmax(0, 1fr) auto minmax(0, 1fr)', gap: 10, alignItems: 'center', fontSize: 14, borderTop: '1px solid var(--line)', paddingTop: 6 }}>
            <span className="chip c-neutral mono" title={m.categoryName}>
              {m.categoryCode}
            </span>
            <Names players={m.sideA} />
            <span className="muted mono" style={{ fontSize: 12 }}>
              v
            </span>
            <Names players={m.sideB} right />
          </li>
        ))}
      </ol>
    </details>
  );
}

function Names({ players, right }: { players: PublicFixtureMatch['sideA']; right?: boolean }) {
  if (players.length === 0) return <span className="muted" style={{ textAlign: right ? 'right' : 'left' }}>To be announced</span>;
  return (
    <span style={{ textAlign: right ? 'right' : 'left' }}>
      {players.map((p, i) => (
        <span key={i}>
          {i > 0 && ' / '}
          {p.slug ? <Link href={`/players/${p.slug}`}>{p.fullName}</Link> : p.fullName}
        </span>
      ))}
    </span>
  );
}

function BracketSide({ team, source, top }: { team: PublicFixtureTeam | null; source: string | null; top?: boolean }) {
  return (
    <div style={{ padding: '8px 10px', borderTop: top ? '1px solid var(--line)' : undefined }}>
      <TeamSide team={team} source={source} />
    </div>
  );
}

type PublicFixtureMatch = PublicFixture['matches'][number];
