import clsx from 'clsx';

export interface GameScore {
  a: number;
  b: number;
}

/** One tile per game: winner bold, loser at 60% (brief §5). Perspective = side A. */
export function GameScoreTiles({ games, compact }: { games: GameScore[]; compact?: boolean }) {
  return (
    <span style={{ display: 'inline-flex', gap: 6 }}>
      {games.map((g, i) => {
        const aWon = g.a > g.b;
        return (
          <span
            key={i}
            className="num"
            title={`Game ${i + 1}`}
            style={{ border: '1px solid var(--line-2)', borderRadius: 'var(--r-xs)', padding: compact ? '2px 6px' : '4px 8px', fontSize: compact ? 14 : 16, background: 'var(--bg-2)' }}
          >
            <span style={{ opacity: aWon ? 1 : 0.6 }}>{g.a}</span>–<span style={{ opacity: aWon ? 0.6 : 1 }}>{g.b}</span>
          </span>
        );
      })}
    </span>
  );
}

export type LotState = 'READY' | 'OPEN' | 'GOING_ONCE' | 'GOING_TWICE' | 'CLOSED';
const STEPS: { key: LotState; label: string }[] = [
  { key: 'READY', label: 'Ready' },
  { key: 'OPEN', label: 'Open' },
  { key: 'GOING_ONCE', label: 'Going once' },
  { key: 'GOING_TWICE', label: 'Going twice' },
  { key: 'CLOSED', label: 'Closed' },
];

/** Ready → Open → Going once → Going twice → Closed, slanted panels (export console). */
export function LotStatusBar({ state }: { state: LotState }) {
  return (
    <ol style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', listStyle: 'none', padding: 0, margin: 0, border: '1px solid var(--line-2)', borderRadius: 'var(--r-s)', overflow: 'hidden' }}>
      {STEPS.map((s) => (
        <li
          key={s.key}
          aria-current={s.key === state ? 'step' : undefined}
          className={clsx('disp')}
          style={{
            fontSize: 14,
            padding: '10px 6px',
            textAlign: 'center',
            borderRight: '1px solid var(--line)',
            background: s.key === state ? 'var(--lime)' : 'transparent',
            color: s.key === state ? 'var(--lime-ink)' : 'var(--text-2)',
          }}
        >
          {s.label}
        </li>
      ))}
    </ol>
  );
}
