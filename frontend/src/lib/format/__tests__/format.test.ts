import { describe, expect, it } from 'vitest';
import { formatCompact, formatLkr, formatRs } from '../money';
import { initials, splitName } from '../names';
import { formatAuditTime } from '../dates';

describe('money', () => {
  it('formats public and admin amounts', () => {
    expect(formatRs(300000)).toBe('Rs. 300,000');
    expect(formatLkr(1250000)).toBe('LKR 1,250,000');
  });
  it('compacts only for the big display', () => {
    expect(formatCompact(1250000)).toBe('1.25M');
    expect(formatCompact(120000)).toBe('120K');
    expect(formatCompact(950)).toBe('950');
  });
});

describe('names', () => {
  it('splits surname for the heavy treatment', () => {
    expect(splitName('Tharshan Sivakumar')).toEqual({ first: 'Tharshan', last: 'Sivakumar' });
    expect(splitName('Madonna')).toEqual({ first: '', last: 'Madonna' });
    expect(initials('Arun Vel')).toBe('AV');
  });
});

describe('dates', () => {
  it('renders audit timestamps in Asia/Colombo (UTC+5:30)', () => {
    expect(formatAuditTime('2026-11-18T08:30:00Z')).toBe('2026-11-18 14:00:00');
  });
});
