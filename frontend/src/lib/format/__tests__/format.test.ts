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

import { fromZonedInput, toZonedInput } from '../dates';

describe('zoned datetime inputs (Asia/Colombo, UTC+5:30)', () => {
  it('round-trips between the input value and UTC', () => {
    expect(fromZonedInput('2026-12-01T09:00')).toBe('2026-12-01T03:30:00.000Z');
    expect(toZonedInput('2026-12-01T03:30:00.000Z')).toBe('2026-12-01T09:00');
    expect(fromZonedInput('')).toBeNull();
    expect(toZonedInput(null)).toBe('');
  });
});

import { formatDateRange } from '../range';

describe('date ranges', () => {
  it('collapses shared month and year', () => {
    expect(formatDateRange('2026-12-12', '2026-12-14')).toBe('12–14 Dec 2026');
    expect(formatDateRange('2026-11-30', '2026-12-02')).toBe('30 Nov – 2 Dec 2026');
    expect(formatDateRange(null, null)).toBeNull();
  });
});
