import clsx from 'clsx';
import Link from 'next/link';
import { CrestTile } from '@/design-system/sport/CrestTile';
import type { GroupTable, Tiebreaker } from '@/lib/api/public';
import s from './Standings.module.css';

export const TIEBREAKER_LABEL: Record<Tiebreaker, string> = {
  MATCHES_WON: 'Matches won',
  MATCH_DIFF: 'Match difference',
  GAME_DIFF: 'Game difference',
  POINT_DIFF: 'Point difference',
  GAMES_WON: 'Games won',
  HEAD_TO_HEAD: 'Head-to-head',
};

const signed = (n: number) => (n > 0 ? `+${n}` : String(n));

export function Form({ form }: { form: string[] }) {
  if (!form.length) return <span className="muted">—</span>;
  return (
    <span className={s.form} aria-label={`Form: ${form.join(' ')}`}>
      {form.slice(-5).map((r, i) => (
        <span key={i} className={clsx(s.f, r === 'W' ? s.fW : r === 'D' ? s.fD : s.fL)} aria-hidden="true">
          {r}
        </span>
      ))}
    </span>
  );
}

/** One group (or league) table. Qualifying places have a lime edge and a dashed line under the cut-off. */
export function StandingsTable({ g, linkTeams = true }: { g: GroupTable; linkTeams?: boolean }) {
  return (
    <div className={s.wrap}>
      <table className={s.table}>
        <caption className="sr-only">{g.name} standings</caption>
        <thead>
          <tr>
            <th className={s.left} scope="col">
              #
            </th>
            <th className={s.left} scope="col">
              Team
            </th>
            <th scope="col" title="Ties played">
              P
            </th>
            <th scope="col" title="Ties won">
              W
            </th>
            <th scope="col" title="Ties drawn">
              D
            </th>
            <th scope="col" title="Ties lost">
              L
            </th>
            <th scope="col" className={s.hideSm} title="Matches won–lost">
              Matches
            </th>
            <th scope="col" className={s.hideSm} title="Game difference">
              Games
            </th>
            <th scope="col" className={s.hideSm} title="Point difference">
              Pts +/−
            </th>
            <th scope="col" title="Table points">
              Pts
            </th>
            <th scope="col" className={clsx(s.left, s.hideSm)}>
              Form
            </th>
          </tr>
        </thead>
        <tbody>
          {g.rows.map((r) => (
            <tr
              key={r.team?.slug ?? r.position}
              className={clsx(r.qualifies && s.q, g.qualifiers > 0 && r.position === g.qualifiers && s.cutoff)}
            >
              <td className={clsx(s.left, s.pos)}>
                {r.position}
                {r.tied && (
                  <span title="Level on every tie-breaker" aria-label="level on every tie-breaker">
                    =
                  </span>
                )}
              </td>
              <td className={s.left}>
                {r.team &&
                  (linkTeams ? (
                    <Link href={`/teams/${r.team.slug}`} className={s.team}>
                      <CrestTile logoUrl={r.team.logoUrl} name={r.team.name} colorSlot={r.team.colorSlot} size="s" />
                      {r.team.name}
                    </Link>
                  ) : (
                    <span className={s.team}>
                      <CrestTile logoUrl={r.team.logoUrl} name={r.team.name} colorSlot={r.team.colorSlot} size="s" />
                      {r.team.name}
                    </span>
                  ))}
              </td>
              <td>{r.played}</td>
              <td>{r.won}</td>
              <td>{r.drawn}</td>
              <td>{r.lost}</td>
              <td className={s.hideSm}>
                {r.matchesWon}–{r.matchesLost}
              </td>
              <td className={s.hideSm}>{signed(r.gamesWon - r.gamesLost)}</td>
              <td className={s.hideSm}>{signed(r.pointsWon - r.pointsLost)}</td>
              <td className={s.pts}>{r.points}</td>
              <td className={clsx(s.left, s.hideSm)}>
                <Form form={r.form} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function GroupHead({ g }: { g: GroupTable }) {
  return (
    <div className={s.head}>
      <h3 className="t-h3" style={{ margin: 0 }}>
        {g.name}
      </h3>
      <span className="t-caption">
        {g.complete ? 'Final' : `${g.tiesPlayed} of ${g.tiesTotal} ties played`}
        {g.qualifiers > 0 ? ` · top ${g.qualifiers} go through` : ''}
      </span>
    </div>
  );
}
