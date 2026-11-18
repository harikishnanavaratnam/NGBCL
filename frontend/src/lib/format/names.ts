/** Split a full name for the "Tharshan SIVAKUMAR" treatment (brief §4: surname heavy). */
export function splitName(full: string): { first: string; last: string } {
  const parts = full.trim().split(/\s+/);
  if (parts.length === 1) return { first: '', last: parts[0] ?? '' };
  const last = parts.pop() as string;
  return { first: parts.join(' '), last };
}

export function initials(full: string): string {
  return full
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? '')
    .join('');
}
