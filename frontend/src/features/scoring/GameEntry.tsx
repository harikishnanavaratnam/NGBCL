'use client';

import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Inputs';
import styles from './Scorer.module.css';

export type GameRows = [string, string][];

export const toRows = (games: { scoreA: number; scoreB: number }[]): GameRows => games.map((g) => [String(g.scoreA), String(g.scoreB)]);

export function parseRows(rows: GameRows): [number, number][] | null {
  const out: [number, number][] = [];
  for (const [a, b] of rows) {
    if (a.trim() === '' && b.trim() === '') continue;
    if (!/^\d{1,2}$/.test(a.trim()) || !/^\d{1,2}$/.test(b.trim())) return null;
    out.push([Number(a), Number(b)]);
  }
  return out;
}

/** Game-by-game score editor (paper scoresheet entry and official corrections). */
export function GameEntry({
  rows,
  onChange,
  maxGames,
  labelA,
  labelB,
}: {
  rows: GameRows;
  onChange: (r: GameRows) => void;
  maxGames: number;
  labelA: string;
  labelB: string;
}) {
  const set = (i: number, j: 0 | 1, v: string) =>
    onChange(rows.map((r, k) => (k === i ? ((j === 0 ? [v, r[1]] : [r[0], v]) as [string, string]) : r)));
  return (
    <div className={styles.games}>
      <div className={styles.gameRow} aria-hidden="true">
        <span />
        <span className="t-caption">{labelA}</span>
        <span />
        <span className="t-caption">{labelB}</span>
        <span />
      </div>
      {rows.map((r, i) => (
        <div key={i} className={styles.gameRow}>
          <span className="t-body-s">Game {i + 1}</span>
          <Input aria-label={`Game ${i + 1} ${labelA}`} inputMode="numeric" value={r[0]} onChange={(e) => set(i, 0, e.target.value)} />
          <span aria-hidden="true">–</span>
          <Input aria-label={`Game ${i + 1} ${labelB}`} inputMode="numeric" value={r[1]} onChange={(e) => set(i, 1, e.target.value)} />
          {rows.length > 1 ? (
            <Button variant="ghost" size="sm" aria-label={`Remove game ${i + 1}`} onClick={() => onChange(rows.filter((_, k) => k !== i))}>
              ×
            </Button>
          ) : (
            <span />
          )}
        </div>
      ))}
      {rows.length < maxGames && (
        <div>
          <Button variant="out" size="sm" onClick={() => onChange([...rows, ['', '']])}>
            Add game
          </Button>
        </div>
      )}
    </div>
  );
}
