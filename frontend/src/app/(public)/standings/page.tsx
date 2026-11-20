import clsx from 'clsx';
import Link from 'next/link';
import { EmptyState } from '@/design-system/data/States';
import { CrestTile } from '@/design-system/sport/CrestTile';
import s from '@/features/public/Public.module.css';
import { PageBand } from '@/features/public/Sections';
import { Form, GroupHead, StandingsTable, TIEBREAKER_LABEL } from '@/features/standings/StandingsTable';
import t from '@/features/standings/Standings.module.css';
import { getPublicStandings, getPublicStats } from '@/lib/api/public';

export const metadata = {
  title: 'Standings',
  description: 'NGBCL 1.0 group tables, team records and the leading players in each event.',
};

const signed = (n: number) => (n > 0 ? `+${n}` : String(n));

/** Public standings (STA-005..009): group tables, team records, event leaders and player records. */
export default async function StandingsPage() {
  const [standings, stats] = await Promise.all([getPublicStandings(), getPublicStats()]);
  const groups = standings?.groups ?? [];
  const rules = standings?.rules;
  const topPlayers = stats.players.slice(0, 20);
  return (
    <>
      <PageBand
        crumb={
          <>
            <Link href="/">Home</Link> / Standings
          </>
        }
        eyebrow="Competition"
        title="Standings"
      >
        <p style={{ margin: 0, maxWidth: 640 }}>
          Tables count confirmed results only. They update a few moments after an official confirms each result.
        </p>
      </PageBand>

      {groups.length === 0 ? (
        <section className={s.sec} style={{ borderTop: 0 }}>
          <div className="wrap">
            <EmptyState
              title="Tables appear once the fixtures are out"
              description="Group tables fill in as tie results are confirmed."
              action={{ label: 'Fixtures', href: '/fixtures' }}
            />
          </div>
        </section>
      ) : (
        <section className={s.sec} style={{ borderTop: 0 }}>
          <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>
            <h2 className={clsx('disp', s.h2)}>{groups.length === 1 ? groups[0]!.name : 'Groups'}</h2>
            {groups.map((g) => (
              <div key={g.name} className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
                {groups.length > 1 && <GroupHead g={g} />}
                <StandingsTable g={g} />
              </div>
            ))}
            {rules && (
              <p className="help" style={{ margin: 0 }}>
                {rules.pointsWin} points for a tie won, {rules.pointsDraw} for a draw
                {rules.pointsLoss ? `, ${rules.pointsLoss} for a loss` : ''}. Teams level on points are separated by{' '}
                {rules.tiebreakers.map((x) => TIEBREAKER_LABEL[x].toLowerCase()).join(', then ') || 'name'}.
              </p>
            )}
          </div>
        </section>
      )}

      {stats.teams.length > 0 && (
        <section className={clsx(s.sec, s.secAlt)}>
          <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h2 className={clsx('disp', s.h2)}>Team records</h2>
            <div className={clsx('card', t.wrap)} style={{ padding: 8 }}>
              <table className={t.table}>
                <thead>
                  <tr>
                    <th className={t.left} scope="col">
                      Team
                    </th>
                    <th scope="col">Ties W–D–L</th>
                    <th scope="col">Matches</th>
                    <th scope="col" className={t.hideSm}>
                      Games
                    </th>
                    <th scope="col" className={clsx(t.left, t.hideSm)}>
                      Last 5
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {stats.teams.map((r) => (
                    <tr key={r.team.slug}>
                      <td className={t.left}>
                        <Link href={`/teams/${r.team.slug}`} className={t.team}>
                          <CrestTile logoUrl={r.team.logoUrl} name={r.team.name} colorSlot={r.team.colorSlot} size="s" />
                          {r.team.name}
                        </Link>
                      </td>
                      <td>
                        {r.tiesWon}–{r.tiesDrawn}–{r.tiesLost}
                      </td>
                      <td>
                        {r.matchesWon}–{r.matchesLost}
                      </td>
                      <td className={t.hideSm}>{signed(r.gamesWon - r.gamesLost)}</td>
                      <td className={clsx(t.left, t.hideSm)}>
                        <Form form={r.form} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      )}

      {stats.leaders.length > 0 && (
        <section className={s.sec}>
          <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h2 className={clsx('disp', s.h2)}>Event leaders</h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 16 }}>
              {stats.leaders.map((c) => (
                <div key={c.code} className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <span className="eyebrow">{c.name}</span>
                  <ol style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {c.leaders.map((l, i) => (
                      <li
                        key={l.slug}
                        style={{ display: 'grid', gridTemplateColumns: '20px minmax(0,1fr) auto', gap: 8, alignItems: 'center' }}
                      >
                        <span className="num muted">{i + 1}</span>
                        <span style={{ minWidth: 0 }}>
                          <Link href={`/players/${l.slug}`} style={{ fontWeight: 600 }}>
                            {l.fullName}
                          </Link>
                          {l.team && <span className="t-caption"> · {l.team.name}</span>}
                        </span>
                        <span className="num" title={`${l.won} won of ${l.played}`}>
                          {l.won}/{l.played}
                        </span>
                      </li>
                    ))}
                  </ol>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}

      {topPlayers.length > 0 && (
        <section className={clsx(s.sec, s.secAlt)}>
          <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h2 className={clsx('disp', s.h2)}>Player records</h2>
            <div className={clsx('card', t.wrap)} style={{ padding: 8 }}>
              <table className={t.table}>
                <thead>
                  <tr>
                    <th className={t.left} scope="col">
                      Player
                    </th>
                    <th className={clsx(t.left, t.hideSm)} scope="col">
                      Team
                    </th>
                    <th scope="col">Played</th>
                    <th scope="col">Won</th>
                    <th scope="col" className={t.hideSm}>
                      Games
                    </th>
                    <th scope="col" className={t.hideSm}>
                      Points
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {topPlayers.map((p) => (
                    <tr key={p.slug}>
                      <td className={t.left}>
                        <Link href={`/players/${p.slug}`} style={{ fontWeight: 600 }}>
                          {p.fullName}
                        </Link>
                      </td>
                      <td className={clsx(t.left, t.hideSm)}>{p.team?.name ?? '—'}</td>
                      <td>{p.played}</td>
                      <td>{p.won}</td>
                      <td className={t.hideSm}>
                        {p.gamesWon}–{p.gamesLost}
                      </td>
                      <td className={t.hideSm}>
                        {p.pointsWon}–{p.pointsLost}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {stats.players.length > topPlayers.length && (
              <p className="help" style={{ margin: 0 }}>
                Showing the top {topPlayers.length} of {stats.players.length} players. Every player’s record is on their profile page.
              </p>
            )}
          </div>
        </section>
      )}
    </>
  );
}
