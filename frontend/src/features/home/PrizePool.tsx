import { formatRs } from '@/lib/format/money';
import styles from './PrizePool.module.css';

export interface Prizes {
  first: number | null;
  second: number | null;
  third: number | null;
}

/** Launch-poster amounts, used until the API answers. */
const FALLBACK: Prizes = { first: 300_000, second: 200_000, third: 100_000 };

/** Prize pool podium (export Main · prize section; amounts from the launch poster / proposal p.4). */
export function PrizePool({ prizes = FALLBACK }: { prizes?: Prizes }) {
  const PRIZES = [
    { key: 'p2', label: 'Runners-up', amount: prizes.second, accent: '#D9DEE7' },
    { key: 'p1', label: 'Champions', amount: prizes.first, accent: 'var(--gold)' },
    { key: 'p3', label: 'Third place', amount: prizes.third, accent: '#B8733A' },
  ].filter((p): p is { key: string; label: string; amount: number; accent: string } => p.amount !== null);
  if (PRIZES.length === 0) return null;
  const total = PRIZES.reduce((s, p) => s + p.amount, 0);
  return (
    <section className={styles.sec} aria-labelledby="prize-title">
      <div className="wrap">
        <span className="eyebrow">Prize pool</span>
        <h2 id="prize-title" className="disp" style={{ fontSize: 'clamp(26px, 3.4vw, 40px)', marginTop: 6 }}>
          Play for <span className="gold">{formatRs(total)}</span>
        </h2>
        <div className={styles.podium}>
          {PRIZES.map((p) => (
            <div key={p.key} className={`${styles.card} ${p.key === 'p1' ? styles.champ : ''}`} style={{ borderTopColor: p.accent }}>
              {p.key === 'p1' && (
                // Decorative, fixed-size, already optimised WebP — a plain img avoids next/image's resize warning.
                // eslint-disable-next-line @next/next/no-img-element
                <img src="/brand/trophy.webp" alt="" width={140} height={171} className={styles.trophy} loading="lazy" decoding="async" />
              )}
              <span className="eyebrow">{p.label}</span>
              <span className="num" style={{ fontSize: p.key === 'p1' ? 52 : 40, color: p.key === 'p1' ? 'var(--gold-2)' : 'var(--text)' }}>
                {formatRs(p.amount)}
              </span>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
