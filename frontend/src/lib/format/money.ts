/**
 * Money is stored as whole LKR (bigint on the backend, number here).
 * Public pages: "Rs. 300,000"; admin tables: label prefix "LKR" + figure; big display: compact "1.25M".
 */
const grouped = new Intl.NumberFormat('en-LK', { maximumFractionDigits: 0 });

export function formatAmount(value: number): string {
  return grouped.format(Math.round(value));
}

export function formatRs(value: number): string {
  return `Rs. ${formatAmount(value)}`;
}

export function formatLkr(value: number): string {
  return `LKR ${formatAmount(value)}`;
}

/** Compact form for the 5 m auction display only (brief §4 rules). */
export function formatCompact(value: number): string {
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  const trim = (n: number) => n.toFixed(2).replace(/\.?0+$/, '');
  if (abs >= 1_000_000) return `${sign}${trim(abs / 1_000_000)}M`;
  if (abs >= 1_000) return `${sign}${trim(abs / 1_000)}K`;
  return `${sign}${abs}`;
}
