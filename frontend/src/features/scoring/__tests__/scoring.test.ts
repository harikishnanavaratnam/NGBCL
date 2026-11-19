import { describe, expect, it } from 'vitest';
import { situation, type MatchState } from '@/lib/api/scoring';
import { project } from '../ScoringScreen';
import { parseRows } from '../GameEntry';
import { rulesSummary } from '../ScoringSettingsPanel';

const rules = { pointsToWin: 21, winBy: 2, pointCap: 30, gamesToWin: 2, custom: false };
const base = { rules, currentGame: 1, scoreA: 0, scoreB: 0, gamesA: 0, gamesB: 0, server: 'A', winnerSide: null } as unknown as MatchState;
const at = (o: Partial<MatchState>) => ({ ...base, ...o }) as MatchState;
const taps = (sides: string) => [...sides].map((c, i) => ({ side: c as 'A' | 'B', id: String(i) }));

describe('situation', () => {
  it('labels game point, deuce, golden point and match point', () => {
    expect(situation(at({ scoreA: 20, scoreB: 15 }))).toBe('Game point');
    expect(situation(at({ scoreA: 20, scoreB: 20 }))).toBe('Deuce');
    expect(situation(at({ scoreA: 29, scoreB: 29 }))).toBe('Golden point');
    expect(situation(at({ scoreA: 20, scoreB: 10, gamesA: 1, currentGame: 2 }))).toBe('Match point');
    expect(situation(at({ scoreA: 10, scoreB: 10 }))).toBeNull();
    expect(situation(at({ currentGame: null }))).toBeNull();
  });
});

describe('project', () => {
  it('adds queued rallies and moves the serve', () => {
    const v = project(at({ scoreA: 3, scoreB: 4 }), taps('AB'));
    expect([v.scoreA, v.scoreB, v.server]).toEqual([4, 5, 'B']);
  });
  it('rolls into the next game and decides the match', () => {
    const g2 = project(at({ scoreA: 20, scoreB: 3 }), taps('A'));
    expect([g2.gamesA, g2.currentGame, g2.scoreA, g2.scoreB]).toEqual([1, 2, 0, 0]);
    const won = project(at({ scoreA: 20, scoreB: 3, gamesA: 1, currentGame: 2 }), taps('AB'));
    expect([won.currentGame, won.winnerSide, won.gamesA]).toEqual([null, 'A', 2]);
  });
  it('respects win-by-two and the cap', () => {
    expect(project(at({ scoreA: 21, scoreB: 20 }), taps('A')).gamesA).toBe(1);
    expect(project(at({ scoreA: 20, scoreB: 20 }), taps('A')).gamesA).toBe(0);
    expect(project(at({ scoreA: 29, scoreB: 29 }), taps('B')).gamesB).toBe(1);
  });
});

describe('helpers', () => {
  it('parses game rows and skips blanks', () => {
    expect(
      parseRows([
        ['21', '15'],
        ['', ''],
      ]),
    ).toEqual([[21, 15]]);
    expect(parseRows([['21', 'x']])).toBeNull();
  });
  it('summarises rules', () => {
    expect(rulesSummary(rules)).toBe('21 pts · win by 2 · cap 30 · best of 3');
    expect(rulesSummary({ pointsToWin: 11, winBy: 1, pointCap: 11, gamesToWin: 1 })).toBe('11 pts · best of 1');
  });
});
