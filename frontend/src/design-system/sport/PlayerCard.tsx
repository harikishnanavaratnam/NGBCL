import Image from 'next/image';
import { splitName } from '@/lib/format/names';
import { teamColor } from '../tokens';
import { Money, PlayerName } from '../primitives/Text';
import { StatusChip } from '../primitives/StatusChip';

export interface PlayerCardData {
  fullName: string;
  photoUrl?: string | null;
  category?: string | null;
  teamName?: string | null;
  teamColorSlot?: number | null;
  price?: number | null;
  priceLabel?: 'Base' | 'Sold';
  status?: string;
  reference?: string;
}

/** Public / admin player card: 4:5 photo, surname heavy, category tag, team colour band. */
export function PlayerCard({ p, variant = 'public' }: { p: PlayerCardData; variant?: 'public' | 'admin' }) {
  const { first, last } = splitName(p.fullName);
  return (
    <article className="ds-player-card" style={{ boxShadow: p.teamColorSlot ? `inset 0 -4px 0 ${teamColor(p.teamColorSlot)}` : undefined }}>
      <div style={{ position: 'relative', aspectRatio: '4 / 5', background: '#1E252E' }}>
        {p.photoUrl ? (
          <Image src={p.photoUrl} alt={p.fullName} fill sizes="(max-width: 700px) 50vw, 240px" style={{ objectFit: 'cover' }} unoptimized />
        ) : (
          <span className="ph-person" role="img" aria-label="Photo placeholder" style={{ position: 'absolute', inset: 0 }} />
        )}
        {variant === 'admin' && p.status && (
          <span style={{ position: 'absolute', top: 8, left: 8 }}>
            <StatusChip status={p.status} />
          </span>
        )}
      </div>
      <div style={{ padding: '12px 14px 16px', display: 'flex', flexDirection: 'column', gap: 6 }}>
        <PlayerName first={first} last={last} />
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
          {p.category && <span className="chip c-neutral">{p.category}</span>}
          {p.teamName && <span className="t-caption">{p.teamName}</span>}
        </div>
        {typeof p.price === 'number' && (
          <span className="t-caption" style={{ display: 'flex', gap: 6, alignItems: 'baseline' }}>
            {p.priceLabel ?? 'Base'} <Money value={p.price} variant="public" className={p.priceLabel === 'Sold' ? 'gold' : undefined} />
          </span>
        )}
        {variant === 'admin' && p.reference && <span className="mono t-caption">{p.reference}</span>}
      </div>
    </article>
  );
}
