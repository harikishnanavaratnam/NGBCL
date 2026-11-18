/**
 * Stored in UTC, shown in the tournament's zone (SRS §6.4). Uses Intl so formatting is
 * deterministic during prerender (no implicit "now").
 */
export const TOURNAMENT_TZ = 'Asia/Colombo';

type Parts = Record<'year' | 'month' | 'day' | 'hour' | 'minute' | 'second', string>;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const cache = new Map<string, Intl.DateTimeFormat>();

function parts(iso: string | Date, tz: string): Parts {
  let f = cache.get(tz);
  if (!f) {
    f = new Intl.DateTimeFormat('en-GB', {
      timeZone: tz,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23',
    });
    cache.set(tz, f);
  }
  const d = typeof iso === 'string' ? new Date(Date.parse(iso)) : iso;
  const out = {} as Parts;
  for (const p of f.formatToParts(d)) if (p.type in { year: 1, month: 1, day: 1, hour: 1, minute: 1, second: 1 }) out[p.type as keyof Parts] = p.value;
  return out;
}

/** "18 Nov 2026" */
export function formatDate(iso: string | Date, tz = TOURNAMENT_TZ) {
  const p = parts(iso, tz);
  return `${Number(p.day)} ${MONTHS[Number(p.month) - 1]} ${p.year}`;
}

/** "18 Nov 2026 · 14:05" */
export function formatDateTime(iso: string | Date, tz = TOURNAMENT_TZ) {
  const p = parts(iso, tz);
  return `${formatDate(iso, tz)} · ${p.hour}:${p.minute}`;
}

/** Audit / mono timestamps: "2026-11-18 14:05:09" */
export function formatAuditTime(iso: string | Date, tz = TOURNAMENT_TZ) {
  const p = parts(iso, tz);
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`;
}

/** ISO instant → value for <input type="datetime-local"> in the tournament's zone ("2026-12-01T09:00"). */
export function toZonedInput(iso: string | null | undefined, tz = TOURNAMENT_TZ): string {
  if (!iso) return '';
  const p = parts(iso, tz);
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}

/** <input type="datetime-local"> value interpreted in the tournament's zone → ISO instant (UTC). */
export function fromZonedInput(local: string, tz = TOURNAMENT_TZ): string | null {
  if (!local) return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(local);
  if (!m) return null;
  const [y, mo, d, h, mi] = m.slice(1).map(Number);
  const wallAsUtc = Date.UTC(y, mo - 1, d, h, mi);
  // Offset of the zone at (approximately) that moment; second pass handles DST edges.
  let guess = wallAsUtc;
  for (let i = 0; i < 2; i++) {
    const p = parts(new Date(guess), tz);
    const shown = Date.UTC(Number(p.year), Number(p.month) - 1, Number(p.day), Number(p.hour), Number(p.minute));
    guess += wallAsUtc - shown;
  }
  return new Date(guess).toISOString();
}
