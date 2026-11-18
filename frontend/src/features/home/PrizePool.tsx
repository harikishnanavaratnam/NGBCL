import Image from 'next/image';
import { formatRs } from '@/lib/format/money';
import styles from './PrizePool.module.css';

const PRIZES = [
  { key: 'p2', label: 'Runners-up', amount: 200_000, accent: '#D9DEE7' },
  { key: 'p1', label: 'Champions', amount: 300_000, accent: 'var(--gold)' },
  { key: 'p3', label: 'Third place', amount: 100_000, accent: '#B8733A' },
] as const;

/** Prize pool podium (export Main · prize section; amounts from the launch poster / proposal p.4). */
export function PrizePool() {
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
              {p.key === 'p1' && <Image src="/brand/trophy.webp" alt="" width={140} height={171} className={styles.trophy} style={{ width: 140, height: 'auto' }} />}
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
