import { describe, expect, it } from 'vitest';
import { sheetName, toCsv } from '../download';

describe('toCsv', () => {
  it('adds a BOM, quotes and escapes, and blocks formulas', () => {
    const csv = toCsv({
      name: 'x',
      columns: ['Name', 'Note', 'Phone', 'Price'],
      rows: [
        ['Siva, K', 'say "hi"', '+94771234567', -5],
        ['=HYPERLINK("x")', null, '@cmd', 10],
      ],
    });
    expect(csv.startsWith('﻿')).toBe(true);
    expect(csv).toContain('"Siva, K","say ""hi""",+94771234567,-5');
    expect(csv).toContain(`"'=HYPERLINK(""x"")",,'@cmd,10`);
  });
});

describe('sheetName', () => {
  it('keeps Excel sheet names valid and unique', () => {
    const used = new Set<string>();
    expect(sheetName('A/B: team [1]', used)).toBe('A B  team  1');
    expect(sheetName('Chavakachcheri Cobras Badminton Club', used)).toHaveLength(31);
    expect(sheetName('Chavakachcheri Cobras Badminton Club', used)).toMatch(/\(2\)$/);
  });
});
