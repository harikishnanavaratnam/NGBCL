'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Inputs';
import { Segmented } from '@/design-system/primitives/Tabs';
import { useToast } from '@/design-system/primitives/Toast';
import { errorMessage } from '@/lib/api/errors';
import { usePublicStatsClient, type Report, type Stats } from '@/lib/api/standings';
import { downloadXlsx } from '../reports/download';
import { Form } from './StandingsTable';
import t from './Standings.module.css';

type View = 'players' | 'teams' | 'leaders';

/** The workbook behind the Excel button: the same numbers as the public pages. */
export function statsReport(s: Stats): Report {
  const day = new Date().toISOString().slice(0, 10);
  return {
    title: 'Statistics',
    fileName: `ngbcl-statistics-${day}`,
    generatedAt: new Date().toISOString(),
    sheets: [
      {
        name: 'Players',
        columns: [
          'Player',
          'Team',
          'Played',
          'Won',
          'Lost',
          'Games won',
          'Games lost',
          'Points won',
          'Points lost',
          'Walkovers won',
          'By event',
        ],
        rows: s.players.map((p) => [
          p.fullName,
          p.team?.name ?? '',
          p.played,
          p.won,
          p.lost,
          p.gamesWon,
          p.gamesLost,
          p.pointsWon,
          p.pointsLost,
          p.walkoversWon,
          p.categories.map((c) => `${c.code} ${c.won}/${c.played}`).join(', '),
        ]),
      },
      {
        name: 'Teams',
        columns: [
          'Team',
          'Ties played',
          'Won',
          'Drawn',
          'Lost',
          'Matches won',
          'Matches lost',
          'Games won',
          'Games lost',
          'Points won',
          'Points lost',
          'Last 5',
        ],
        rows: s.teams.map((r) => [
          r.team.name,
          r.tiesPlayed,
          r.tiesWon,
          r.tiesDrawn,
          r.tiesLost,
          r.matchesWon,
          r.matchesLost,
          r.gamesWon,
          r.gamesLost,
          r.pointsWon,
          r.pointsLost,
          r.form.join(''),
        ]),
      },
      {
        name: 'Event leaders',
        columns: ['Event', 'Rank', 'Player', 'Team', 'Played', 'Won', 'Game difference'],
        rows: s.leaders.flatMap((c) =>
          c.leaders.map((l, i) => [c.name, i + 1, l.fullName, l.team?.name ?? '', l.played, l.won, l.gameDiff]),
        ),
      },
    ],
  };
}

/** Admin › Results › Statistics: player and team records and event leaders (confirmed, published results). */
export function AdminStatistics() {
  const { data, isLoading, error, refetch } = usePublicStatsClient();
  const [view, setView] = useState<View>('players');
  const [q, setQ] = useState('');
  const toast = useToast();
  const players = useMemo(
    () => (data?.players ?? []).filter((p) => !q || `${p.fullName} ${p.team?.name ?? ''}`.toLowerCase().includes(q.toLowerCase())),
    [data, q],
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, flex: 1 }}>
          <nav aria-label="Breadcrumb" className="t-caption">
            Results / Statistics
          </nav>
          <h1 className="t-h1">Statistics</h1>
          <p className="muted t-body-s" style={{ margin: 0 }}>
            Confirmed results in published ties — the same numbers as the public site. Walkovers count for the team, not as a player’s
            match.
          </p>
        </div>
        <Button
          variant="out"
          disabled={!data}
          onClick={() => data && void downloadXlsx(statsReport(data)).catch((e: unknown) => toast.error(errorMessage(e)))}
        >
          Excel
        </Button>
      </div>
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <Segmented
          label="Statistics view"
          value={view}
          onChange={setView}
          options={[
            { value: 'players', label: 'Players' },
            { value: 'teams', label: 'Teams' },
            { value: 'leaders', label: 'Event leaders' },
          ]}
        />
        {view === 'players' && (
          <Input
            aria-label="Search players"
            placeholder="Search players or teams"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            style={{ maxWidth: 280 }}
          />
        )}
      </div>
      {isLoading ? (
        <Skeleton height={320} />
      ) : error || !data ? (
        <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />
      ) : data.players.length === 0 && data.teams.length === 0 ? (
        <EmptyState title="No confirmed results yet" description="Records start with the first confirmed result in a published tie." />
      ) : view === 'players' ? (
        <div className="card" style={{ padding: 8 }}>
          <div className={t.wrap}>
            <table className={t.table}>
              <thead>
                <tr>
                  <th className={t.left}>Player</th>
                  <th className={t.left}>Team</th>
                  <th>Played</th>
                  <th>Won</th>
                  <th>Lost</th>
                  <th>Games</th>
                  <th>Points</th>
                  <th className={t.left}>By event</th>
                </tr>
              </thead>
              <tbody>
                {players.map((p) => (
                  <tr key={p.slug}>
                    <td className={t.left}>
                      <Link href={`/players/${p.slug}`} target="_blank">
                        {p.fullName}
                      </Link>
                    </td>
                    <td className={t.left}>{p.team?.name ?? '—'}</td>
                    <td>{p.played}</td>
                    <td>{p.won}</td>
                    <td>{p.lost}</td>
                    <td>
                      {p.gamesWon}–{p.gamesLost}
                    </td>
                    <td>
                      {p.pointsWon}–{p.pointsLost}
                    </td>
                    <td className={t.left}>{p.categories.map((c) => `${c.code} ${c.won}/${c.played}`).join(' · ')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : view === 'teams' ? (
        <div className="card" style={{ padding: 8 }}>
          <div className={t.wrap}>
            <table className={t.table}>
              <thead>
                <tr>
                  <th className={t.left}>Team</th>
                  <th>Ties W–D–L</th>
                  <th>Matches</th>
                  <th>Games</th>
                  <th>Points</th>
                  <th className={t.left}>Last 5</th>
                </tr>
              </thead>
              <tbody>
                {data.teams.map((r) => (
                  <tr key={r.team.slug}>
                    <td className={t.left}>{r.team.name}</td>
                    <td>
                      {r.tiesWon}–{r.tiesDrawn}–{r.tiesLost}
                    </td>
                    <td>
                      {r.matchesWon}–{r.matchesLost}
                    </td>
                    <td>
                      {r.gamesWon}–{r.gamesLost}
                    </td>
                    <td>
                      {r.pointsWon}–{r.pointsLost}
                    </td>
                    <td className={t.left}>
                      <Form form={r.form} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
          {data.leaders.map((c) => (
            <div key={c.code} className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <strong>{c.name}</strong>
              <ol style={{ margin: 0, paddingLeft: 20, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {c.leaders.map((l) => (
                  <li key={l.slug}>
                    {l.fullName} <span className="t-caption">{l.team?.name}</span> —{' '}
                    <span className="num">
                      {l.won}/{l.played}
                    </span>
                  </li>
                ))}
              </ol>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
